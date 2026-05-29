package com.kbo.stats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.GameBatterLog;
import com.kbo.stats.domain.GameMatchup;
import com.kbo.stats.domain.GamePitcherLog;
import com.kbo.stats.domain.GameStatus;
import com.kbo.stats.mapper.GameBatterLogMapper;
import com.kbo.stats.mapper.GameMapper;
import com.kbo.stats.mapper.GameMatchupMapper;
import com.kbo.stats.mapper.GamePitcherLogMapper;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchupRebuildService {

    private final GameBatterLogMapper batterLogMapper;
    private final GamePitcherLogMapper pitcherLogMapper;
    private final GameMatchupMapper matchupMapper;
    private final GameMapper gameMapper;
    private final PlayerService playerService;
    private final ObjectMapper objectMapper;

    /**
     * 전체 FINISHED 게임 매치업 재구성.
     * 한 게임 실패해도 나머지 계속 진행.
     */
    public Map<String, Object> rebuildAll() {
        log.info("[매치업] 전체 재구성 시작");

        List<Long> finishedGameIds = gameMapper.findIdsByStatus(GameStatus.FINISHED);
        log.info("[매치업] 대상 게임 {}건", finishedGameIds.size());

        int success = 0;
        int failed = 0;
        int totalMatchups = 0;
        List<Long> failedIds = new ArrayList<>();

        for (Long gameId : finishedGameIds) {
            try {
                int count = rebuildOne(gameId);
                totalMatchups += count;
                success++;
                if (success % 50 == 0) {
                    log.info("[매치업] 진행 {}/{}", success, finishedGameIds.size());
                }
            } catch (Exception e) {
                log.error("[매치업] 실패 gameId={} error={}", gameId, e.getMessage());
                failedIds.add(gameId);
                failed++;
            }
        }

        log.info("[매치업] 전체 재구성 완료 success={} failed={} totalMatchups={}",
                success, failed, totalMatchups);

        return Map.of(
                "total", finishedGameIds.size(),
                "success", success,
                "failed", failed,
                "totalMatchups", totalMatchups,
                "failedGameIds", failedIds
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int rebuildOne(Long gameId) {
        log.info("[매치업] 재구성 시작 gameId={}", gameId);

        matchupMapper.deleteByGameId(gameId);

        Game game = gameMapper.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("게임 미존재: " + gameId));

        List<GameBatterLog>  awayBatters  = batterLogMapper.findByGameIdAndTeamSide(gameId, "AWAY");
        List<GameBatterLog>  homeBatters  = batterLogMapper.findByGameIdAndTeamSide(gameId, "HOME");
        List<GamePitcherLog> awayPitchers = pitcherLogMapper.findByGameIdAndTeamSide(gameId, "AWAY");
        List<GamePitcherLog> homePitchers = pitcherLogMapper.findByGameIdAndTeamSide(gameId, "HOME");

        List<GameMatchup> matchups = new ArrayList<>();
        matchups.addAll(buildMatchups(gameId, game, awayBatters, homePitchers, "AWAY"));
        matchups.addAll(buildMatchups(gameId, game, homeBatters, awayPitchers, "HOME"));

        if (!matchups.isEmpty()) {
            matchupMapper.insertBatch(matchups);
        }
        log.info("[매치업] 재구성 완료 gameId={} count={}", gameId, matchups.size());
        return matchups.size();
    }

    private List<GameMatchup> buildMatchups(
            Long gameId,
            Game game,
            List<GameBatterLog> batters,
            List<GamePitcherLog> pitchers,
            String batterTeamSide) {

        if (batters.isEmpty() || pitchers.isEmpty()) return List.of();

        // batting_order별 그룹화 (대타: 같은 타순에 여러 선수)
        Map<Integer, List<GameBatterLog>> byOrder = batters.stream()
                .collect(Collectors.groupingBy(
                        GameBatterLog::getBattingOrder,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int maxInning = batters.stream()
                .map(b -> parseInningResults(b.getInningResults()).size())
                .max(Integer::compare)
                .orElse(9);

        // ① 이닝 시퀀스 구성: 이닝별 출장 타자를 타석 순서대로 나열
        List<MatchupCandidate> sequence = new ArrayList<>();
        int lastBattingOrder = 0;

        for (int inning = 1; inning <= maxInning; inning++) {
            int startOrder = (lastBattingOrder == 0 || lastBattingOrder == 9) ? 1 : lastBattingOrder + 1;
            int currentOrder = startOrder;
            int safetyCounter = 0;
            int inningBatterCount = 0;
            int inningLastOrder = startOrder;

            while (safetyCounter < 50) {
                safetyCounter++;

                List<GameBatterLog> candidates = byOrder.getOrDefault(currentOrder, List.of());
                String result = null;
                GameBatterLog batter = null;
                for (GameBatterLog b : candidates) {
                    String r = getResultAtInning(b.getInningResults(), inning);
                    if (r != null) {
                        result = r;
                        batter = b;
                        break;
                    }
                }

                if (result == null) break; // 해당 타순이 이 이닝에 출장 안 함 → 이닝 종료

                sequence.add(new MatchupCandidate(inning, currentOrder, batter, result));
                inningBatterCount++;
                inningLastOrder = currentOrder;

                currentOrder = (currentOrder % 9) + 1; // 1→2→...→9→1 순환
                if (currentOrder == startOrder) break;  // 일순 방지
            }

            if (inningBatterCount > 0) {
                lastBattingOrder = inningLastOrder;
            }
        }

        // ② 투수 누적 타석 range 계산 (pitch_order 오름차순)
        pitchers.sort(Comparator.comparingInt(GamePitcherLog::getPitchOrder));
        List<PitcherRange> pitcherRanges = new ArrayList<>();
        int cumulative = 0;
        for (GamePitcherLog p : pitchers) {
            int faced = p.getBattersFaced();
            if (faced > 0) {
                pitcherRanges.add(new PitcherRange(p.getPlayerName(), p.getPitchOrder(),
                        cumulative + 1, cumulative + faced));
                cumulative += faced;
            }
        }

        // ③ 시퀀스 + 투수 range 매칭
        String batterTeam  = "AWAY".equals(batterTeamSide) ? game.getAwayTeam() : game.getHomeTeam();
        String pitcherTeam = "AWAY".equals(batterTeamSide) ? game.getHomeTeam() : game.getAwayTeam();

        // 게임 단위 player_id 캐시 (중복 쿼리 방지)
        Map<String, Long> playerIdCache = new HashMap<>();

        List<GameMatchup> result = new ArrayList<>();
        for (int i = 0; i < sequence.size(); i++) {
            int atBatOrder = i + 1;
            MatchupCandidate c = sequence.get(i);
            PitcherRange pitcher = pitcherRanges.stream()
                    .filter(pr -> atBatOrder >= pr.start && atBatOrder <= pr.end)
                    .findFirst()
                    .orElse(null);

            if (pitcher == null) {
                log.warn("[매치업] 투수 못 찾음: gameId={} side={} atBatOrder={} batter={}",
                        gameId, batterTeamSide, atBatOrder, c.batter.getPlayerName());
                continue;
            }

            String batterKey  = c.batter.getPlayerName() + "|" + batterTeam;
            String pitcherKey = pitcher.name + "|" + pitcherTeam;

            Long batterPlayerId = playerIdCache.computeIfAbsent(batterKey, k ->
                    playerService.findOrCreateStubId(c.batter.getPlayerName(), batterTeam, "BATTER"));
            Long pitcherPlayerId = playerIdCache.computeIfAbsent(pitcherKey, k ->
                    playerService.findOrCreateStubId(pitcher.name, pitcherTeam, "PITCHER"));

            result.add(GameMatchup.builder()
                    .gameId(gameId)
                    .inning(c.inning)
                    .atBatOrder(atBatOrder)
                    .batterTeamSide(batterTeamSide)
                    .batterName(c.batter.getPlayerName())
                    .batterPlayerId(batterPlayerId)
                    .batterBattingOrder(c.battingOrder)
                    .pitcherName(pitcher.name)
                    .pitcherPlayerId(pitcherPlayerId)
                    .pitcherPitchOrder(pitcher.pitchOrder)
                    .result(c.result)
                    .build());
        }

        return result;
    }

    private List<String> parseInningResults(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.error("inning_results 파싱 실패: {}", json, e);
            return List.of();
        }
    }

    private String getResultAtInning(String json, int inning) {
        List<String> list = parseInningResults(json);
        int idx = inning - 1;
        if (idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    @AllArgsConstructor
    private static class MatchupCandidate {
        int inning;
        int battingOrder;
        GameBatterLog batter;
        String result;
    }

    @AllArgsConstructor
    private static class PitcherRange {
        String name;
        int pitchOrder;
        int start;
        int end;
    }
}
