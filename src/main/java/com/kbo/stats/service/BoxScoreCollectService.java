package com.kbo.stats.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbo.stats.domain.*;
import com.kbo.stats.dto.BoxScoreCollectResult;
import com.kbo.stats.dto.BoxScoreCollectResult.Status;
import com.kbo.stats.external.kbo.KboBoxScoreApiClient;
import com.kbo.stats.external.kbo.KboGameIdBuilder;
import com.kbo.stats.external.kbo.dto.KboBoxScoreResponse;
import com.kbo.stats.external.kbo.dto.KboScoreBoardResponse;
import com.kbo.stats.external.kbo.dto.parsed.*;
import com.kbo.stats.external.kbo.parser.KboResponseParser;
import com.kbo.stats.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoxScoreCollectService {

    private final GameMapper gameMapper;
    private final GameBoxScoreMapper boxScoreMapper;
    private final GameInningScoreMapper inningScoreMapper;
    private final GameEventMapper eventMapper;
    private final GameBatterLogMapper batterLogMapper;
    private final GamePitcherLogMapper pitcherLogMapper;
    private final KboBoxScoreApiClient apiClient;
    private final KboResponseParser parser;
    private final ObjectMapper objectMapper;

    /**
     * 단일 게임 박스스코어 수집 + DB 영속.
     * <p>
     * FINISHED 아닌 경기는 SKIPPED, API 실패는 FAILED, DB 실패는 예외 전파(트랜잭션 롤백).
     * </p>
     */
    @CacheEvict(value = "gameDetail", key = "#gameId")
    @Transactional
    public BoxScoreCollectResult collectOne(Long gameId) {
        // 1. 게임 조회
        Game game = gameMapper.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("게임 미존재: " + gameId));

        // 2. FINISHED 아닌 경기 스킵
        if (game.getStatus() != GameStatus.FINISHED) {
            log.info("[박스스코어] SKIP gameId={} status={}", gameId, game.getStatus());
            return BoxScoreCollectResult.skipped(gameId, game.getKboGameId(), game.getStatus().name());
        }

        // 3. kboGameId 확정 (없으면 빌드 후 DB 업데이트)
        String kboGameId = game.getKboGameId();
        if (kboGameId == null) {
            kboGameId = KboGameIdBuilder.build(game.getGameDate(), game.getAwayTeam(), game.getHomeTeam());
            gameMapper.updateKboGameId(gameId, kboGameId);
            log.info("[박스스코어] kboGameId 생성 gameId={} kboGameId={}", gameId, kboGameId);
        }

        // 4. API 호출 — 실패 시 FAILED 반환 (트랜잭션은 kboGameId update만 포함하여 커밋)
        KboScoreBoardResponse sb;
        KboBoxScoreResponse bs;
        try {
            int seasonId = game.getGameDate().getYear();
            sb = apiClient.fetchScoreBoard(seasonId, kboGameId);
            bs = apiClient.fetchBoxScore(seasonId, kboGameId);
        } catch (Exception e) {
            log.error("[박스스코어] API 실패 gameId={} kboGameId={}: {}", gameId, kboGameId, e.getMessage(), e);
            return BoxScoreCollectResult.failed(gameId, kboGameId, e.getMessage());
        }

        // 5. 파싱
        ParsedBoxScore parsed = parser.parseAll(sb, bs);

        // 6. 트랜잭션 내 delete + insert
        persistAll(gameId, parsed);

        log.info("[박스스코어] SUCCESS gameId={} kboGameId={}", gameId, kboGameId);
        return BoxScoreCollectResult.success(gameId, kboGameId);
    }

    // ── 영속 (collectOne 트랜잭션 내부에서 호출) ─────────────────

    private void persistAll(Long gameId, ParsedBoxScore parsed) {
        // 기존 데이터 삭제 (재수집 멱등성)
        boxScoreMapper.deleteByGameId(gameId);
        inningScoreMapper.deleteByGameId(gameId);
        eventMapper.deleteByGameId(gameId);
        batterLogMapper.deleteByGameId(gameId);
        pitcherLogMapper.deleteByGameId(gameId);

        // game_boxscore
        ParsedGameMeta m = parsed.getMeta();
        boxScoreMapper.upsert(GameBoxScore.builder()
                .gameId(gameId)
                .leId(m.getLeId()).srId(m.getSrId()).seasonId(m.getSeasonId())
                .crowdCn(m.getCrowdCount())
                .startTm(m.getStartTime()).endTm(m.getEndTime()).useTm(m.getUseTime())
                .maxInning(m.getMaxInning()).realMaxInning(m.getRealMaxInning())
                .homeSeasonW(m.getHomeSeasonW()).homeSeasonL(m.getHomeSeasonL()).homeSeasonD(m.getHomeSeasonD())
                .awaySeasonW(m.getAwaySeasonW()).awaySeasonL(m.getAwaySeasonL()).awaySeasonD(m.getAwaySeasonD())
                .homeHits(m.getHomeHits()).homeErrors(m.getHomeErrors()).homeWalks(m.getHomeWalks())
                .awayHits(m.getAwayHits()).awayErrors(m.getAwayErrors()).awayWalks(m.getAwayWalks())
                .finishingHit(m.getFinishingHit()).umpires(m.getUmpires())
                .build());

        // game_inning_score
        for (ParsedInningScore is : parsed.getInningScores()) {
            inningScoreMapper.insert(GameInningScore.builder()
                    .gameId(gameId)
                    .inning(is.getInning())
                    .teamSide(is.getTeamSide())
                    .score(is.getScore())
                    .build());
        }

        // game_event
        for (ParsedGameEvent e : parsed.getEvents()) {
            eventMapper.insert(GameEvent.builder()
                    .gameId(gameId)
                    .eventType(e.getEventType())
                    .playerName(e.getPlayerName())
                    .inning(e.getInning())
                    .seasonCount(e.getSeasonCount())
                    .runs(e.getRuns())
                    .opponentPitcher(e.getOpponentPitcher())
                    .rawText(e.getRawText())
                    .build());
        }

        // game_batter_log (AWAY + HOME)
        for (ParsedBatterLine b : parsed.getAwayBatters())
            insertBatter(gameId, b);
        for (ParsedBatterLine b : parsed.getHomeBatters())
            insertBatter(gameId, b);

        // game_pitcher_log (AWAY + HOME)
        for (ParsedPitcherLine p : parsed.getAwayPitchers())
            insertPitcher(gameId, p);
        for (ParsedPitcherLine p : parsed.getHomePitchers())
            insertPitcher(gameId, p);
    }

    private void insertBatter(Long gameId, ParsedBatterLine b) {
        String inningResultsJson;
        try {
            inningResultsJson = objectMapper.writeValueAsString(b.getInningResults());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("inning_results JSON 직렬화 실패: " + b.getPlayerName(), e);
        }
        batterLogMapper.insert(GameBatterLog.builder()
                .gameId(gameId)
                .teamSide(b.getTeamSide())
                .battingOrder(b.getBattingOrder())
                .position(b.getPosition())
                .playerName(b.getPlayerName())
                .atBats(b.getAtBats())
                .hits(b.getHits())
                .rbi(b.getRbi())
                .walks(b.getWalks())
                .seasonAvg(b.getSeasonAvg())
                .inningResults(inningResultsJson)
                .build());
    }

    private void insertPitcher(Long gameId, ParsedPitcherLine p) {
        pitcherLogMapper.insert(GamePitcherLog.builder()
                .gameId(gameId)
                .teamSide(p.getTeamSide())
                .pitchOrder(p.getPitchOrder())
                .playerName(p.getPlayerName())
                .appearanceLabel(p.getAppearanceLabel())
                .result(p.getResult())
                .seasonW(p.getSeasonW())
                .seasonL(p.getSeasonL())
                .seasonS(p.getSeasonS())
                .inningsPitchedOuts(p.getInningsPitchedOuts())
                .battersFaced(p.getBattersFaced())
                .pitches(p.getPitches())
                .atBatsAgainst(p.getAtBatsAgainst())
                .hitsAgainst(p.getHitsAgainst())
                .homeRunsAgainst(p.getHomeRunsAgainst())
                .walksHbp(p.getWalksHbp())
                .strikeouts(p.getStrikeouts())
                .runsAllowed(p.getRunsAllowed())
                .earnedRuns(p.getEarnedRuns())
                .seasonEra(p.getSeasonEra())
                .build());
    }
}
