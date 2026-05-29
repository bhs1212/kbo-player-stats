package com.kbo.stats.service;

import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.TeamLeadersDto;
import com.kbo.stats.dto.TeamStandingDto;
import com.kbo.stats.mapper.GameMapper;
import com.kbo.stats.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final GameMapper gameMapper;
    private final PlayerMapper playerMapper;
    private final PlayerService playerService;

    /** 팀 순위 목록 (승률 기준 정렬, 5분 캐싱) */
    @Cacheable("teamStandings")
    public List<TeamStandingDto> getTeamStandings() {
        log.info("팀 순위 조회 시작 (캐시 미스)");
        try {
            List<TeamStandingDto> standings = gameMapper.findTeamStandings();
            for (int i = 0; i < standings.size(); i++) {
                standings.get(i).setRank(i + 1);
            }
            log.info("팀 순위 조회 완료: {}개 팀", standings.size());
            return standings;
        } catch (Exception e) {
            log.error("팀 순위 조회 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /** 특정 팀의 순위 정보 */
    public Optional<TeamStandingDto> getTeamStanding(String team) {
        log.debug("팀 순위 조회: team={}", team);
        return getTeamStandings().stream()
                .filter(s -> team.equals(s.getTeam()))
                .findFirst();
    }

    /** 시즌 랭킹 TOP N (5분 캐싱) */
    @Cacheable(value = "seasonRankings", key = "#type + '_' + #limit")
    public List<Player> getSeasonRanking(String type, int limit) {
        log.info("시즌 랭킹 조회 시작 (캐시 미스): type={}, limit={}", type, limit);
        try {
            List<Player> result = switch (type) {
                case "batting" -> playerService.getBattingRanking(limit);
                case "homerun" -> playerService.getHomeRunRanking(limit);
                case "era" -> playerService.getEraRanking(limit);
                case "wins" -> playerService.getWinsRanking(limit);
                default -> List.of();
            };
            log.info("시즌 랭킹 조회 완료: type={}, 결과={}명", type, result.size());
            return result;
        } catch (Exception e) {
            log.error("시즌 랭킹 조회 실패: type={}, error={}", type, e.getMessage(), e);
            return List.of();
        }
    }

    /** 팀 주요 선수 리더 (타율/홈런/방어율 각 1위) */
    public TeamLeadersDto getTeamLeaders(String team) {
        log.info("팀 주요 선수 조회 시작: team={}", team);
        try {
            TeamLeadersDto result = TeamLeadersDto.builder()
                    .team(team)
                    .battingLeader(playerMapper.findBattingLeaderByTeam(team).orElse(null))
                    .homeRunLeader(playerMapper.findHomeRunLeaderByTeam(team).orElse(null))
                    .eraLeader(playerMapper.findEraLeaderByTeam(team).orElse(null))
                    .build();
            log.info("팀 주요 선수 조회 완료: team={}, batting={}, hr={}, era={}",
                    team,
                    result.getBattingLeader() != null ? result.getBattingLeader().getName() : "없음",
                    result.getHomeRunLeader() != null ? result.getHomeRunLeader().getName() : "없음",
                    result.getEraLeader() != null ? result.getEraLeader().getName() : "없음");
            return result;
        } catch (Exception e) {
            log.error("팀 주요 선수 조회 실패: team={}, error={}", team, e.getMessage(), e);
            return TeamLeadersDto.builder().team(team).build();
        }
    }

    /** 특정 팀 다음 예정 경기 */
    public Optional<Game> getNextTeamGame(String team, LocalDate fromDate) {
        log.debug("다음 경기 조회: team={}, from={}", team, fromDate);
        try {
            return gameMapper.findNextByTeam(team, fromDate);
        } catch (Exception e) {
            log.error("다음 경기 조회 실패: team={}, error={}", team, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
