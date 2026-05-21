package com.kbo.stats.service;

import com.kbo.stats.domain.PitcherStats;
import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import com.kbo.stats.domain.StatValidationLog;
import com.kbo.stats.dto.BatterBoxScoreAggregate;
import com.kbo.stats.dto.PitcherBoxScoreAggregate;
import com.kbo.stats.mapper.GameBatterLogMapper;
import com.kbo.stats.mapper.GamePitcherLogMapper;
import com.kbo.stats.mapper.PitcherStatsMapper;
import com.kbo.stats.mapper.PlayerMapper;
import com.kbo.stats.mapper.StatValidationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoxScoreCrossValidationService {

    private static final RoundingMode RM                = RoundingMode.HALF_UP;
    private static final BigDecimal   TOLERANCE_AVG     = new BigDecimal("0.001");
    private static final BigDecimal   TOLERANCE_ERA_WHIP = new BigDecimal("0.05");

    private final PlayerMapper            playerMapper;
    private final GameBatterLogMapper     gameBatterLogMapper;
    private final GamePitcherLogMapper    gamePitcherLogMapper;
    private final PitcherStatsMapper      pitcherStatsMapper;
    private final StatValidationLogMapper statValidationLogMapper;

    public void runCrossValidation() {
        log.info("=== 박스스코어 교차 검증 시작 ===");
        int batterTotal = 0, batterPass = 0, batterFail = 0, batterSkip = 0;
        int pitcherTotal = 0, pitcherPass = 0, pitcherFail = 0, pitcherSkip = 0;

        // === 타자: BATTING_AVG_BOXSCORE ===
        for (Player batter : playerMapper.findAllByPlayerType(PlayerType.BATTER)) {
            BatterBoxScoreAggregate agg = gameBatterLogMapper.aggregateByPlayerId(batter.getId());
            if (agg == null || agg.getTotalAtBats() == null || agg.getTotalAtBats() == 0) {
                batterSkip++; continue;
            }
            if (batter.getBattingAvg() == null) { batterSkip++; continue; }

            BigDecimal calculated = BigDecimal.valueOf(agg.getTotalHits())
                    .divide(BigDecimal.valueOf(agg.getTotalAtBats()), 3, RM);
            BigDecimal expected   = BigDecimal.valueOf(batter.getBattingAvg()).setScale(3, RM);
            BigDecimal diff       = calculated.subtract(expected).abs();
            boolean    matched    = diff.compareTo(TOLERANCE_AVG) <= 0;

            saveLog(batter.getId(), "BATTING_AVG_BOXSCORE", expected, calculated, diff, matched);
            batterTotal++;
            if (matched) {
                batterPass++;
            } else {
                batterFail++;
                log.warn("[교차검증][타자] {} ({}) 불일치: 계산={} KBO={}",
                        batter.getName(), batter.getTeam(), calculated, expected);
            }
        }

        // === 투수: ERA_BOXSCORE / WHIP_BOXSCORE ===
        for (Player pitcher : playerMapper.findAllByPlayerType(PlayerType.PITCHER)) {
            PitcherBoxScoreAggregate agg = gamePitcherLogMapper.aggregateByPlayerId(pitcher.getId());
            if (agg == null || agg.getTotalInningsOuts() == null || agg.getTotalInningsOuts() == 0) {
                pitcherSkip++; continue;
            }

            // ERA = earnedRuns * 27 / totalInningsOuts
            if (pitcher.getEra() != null) {
                BigDecimal calculatedEra = BigDecimal.valueOf(agg.getTotalEarnedRuns())
                        .multiply(BigDecimal.valueOf(27))
                        .divide(BigDecimal.valueOf(agg.getTotalInningsOuts()), 2, RM);
                BigDecimal expectedEra   = BigDecimal.valueOf(pitcher.getEra()).setScale(2, RM);
                BigDecimal diffEra       = calculatedEra.subtract(expectedEra).abs();
                boolean    matchedEra    = diffEra.compareTo(TOLERANCE_ERA_WHIP) <= 0;

                saveLog(pitcher.getId(), "ERA_BOXSCORE", expectedEra, calculatedEra, diffEra, matchedEra);
                pitcherTotal++;
                if (matchedEra) {
                    pitcherPass++;
                } else {
                    pitcherFail++;
                    log.warn("[교차검증][투수ERA] {} ({}) 불일치: 계산={} KBO={}",
                            pitcher.getName(), pitcher.getTeam(), calculatedEra, expectedEra);
                }
            }

            // WHIP = (walksHbp + hitsAgainst) * 3 / totalInningsOuts
            PitcherStats pitcherStats = pitcherStatsMapper.findByPlayerId(pitcher.getId());
            if (pitcherStats != null && pitcherStats.getWhip() != null) {
                BigDecimal calculatedWhip = BigDecimal.valueOf(
                                (long) agg.getTotalWalksHbp() + agg.getTotalHitsAgainst())
                        .multiply(BigDecimal.valueOf(3))
                        .divide(BigDecimal.valueOf(agg.getTotalInningsOuts()), 2, RM);
                BigDecimal expectedWhip   = pitcherStats.getWhip().setScale(2, RM);
                BigDecimal diffWhip       = calculatedWhip.subtract(expectedWhip).abs();
                boolean    matchedWhip    = diffWhip.compareTo(TOLERANCE_ERA_WHIP) <= 0;

                saveLog(pitcher.getId(), "WHIP_BOXSCORE", expectedWhip, calculatedWhip, diffWhip, matchedWhip);
                pitcherTotal++;
                if (matchedWhip) {
                    pitcherPass++;
                } else {
                    pitcherFail++;
                    log.warn("[교차검증][투수WHIP] {} ({}) 불일치: 계산={} KBO={}",
                            pitcher.getName(), pitcher.getTeam(), calculatedWhip, expectedWhip);
                }
            }
        }

        log.info("=== 박스스코어 교차 검증 완료 ===");
        log.info("타자: 시도={} PASS={} FAIL={} SKIP={}", batterTotal, batterPass, batterFail, batterSkip);
        log.info("투수: 시도={} PASS={} FAIL={} SKIP={}", pitcherTotal, pitcherPass, pitcherFail, pitcherSkip);
    }

    private void saveLog(Long playerId, String metricName,
                         BigDecimal siteValue, BigDecimal calculatedValue,
                         BigDecimal diff, boolean isMatch) {
        statValidationLogMapper.insert(StatValidationLog.builder()
                .playerId(playerId)
                .metricName(metricName)
                .siteValue(siteValue)
                .calculatedValue(calculatedValue)
                .diff(diff)
                .isMatch(isMatch)
                .build());
    }
}
