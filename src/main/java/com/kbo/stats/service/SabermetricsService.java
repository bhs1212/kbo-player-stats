package com.kbo.stats.service;

import com.kbo.stats.domain.BatterStats;
import com.kbo.stats.domain.PitcherStats;
import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import com.kbo.stats.domain.StatValidationLog;
import com.kbo.stats.dto.BatterLeagueTotalsDto;
import com.kbo.stats.dto.LeagueTotalsDto;
import com.kbo.stats.mapper.BatterStatsMapper;
import com.kbo.stats.mapper.PitcherStatsMapper;
import com.kbo.stats.mapper.PlayerMapper;
import com.kbo.stats.mapper.StatValidationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SabermetricsService {

    private static final int SCALE_RATE  = 3;
    private static final int SCALE_RATIO = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.001");
    private static final int DEFAULT_PERCENTILE = 50;

    // wOBA MLB 표준 가중치
    private static final BigDecimal W_BB  = new BigDecimal("0.69");
    private static final BigDecimal W_HBP = new BigDecimal("0.72");
    private static final BigDecimal W_1B  = new BigDecimal("0.89");
    private static final BigDecimal W_2B  = new BigDecimal("1.27");
    private static final BigDecimal W_3B  = new BigDecimal("1.62");
    private static final BigDecimal W_HR  = new BigDecimal("2.10");

    private final BatterStatsMapper batterStatsMapper;
    private final PitcherStatsMapper pitcherStatsMapper;
    private final StatValidationLogMapper statValidationLogMapper;
    private final PlayerMapper playerMapper;

    @Autowired @Lazy
    private SabermetricsService self;

    // ──────────────────────────────────────────────────────────────
    // 타자 지표 계산
    // ──────────────────────────────────────────────────────────────

    /** 출루율: (H + BB + HBP) / (AB + BB + HBP + SF) */
    public BigDecimal calculateOBP(BatterStats stats, Integer hits) {
        if (stats == null || hits == null) return null;
        Integer bb  = stats.getWalks();
        Integer hbp = stats.getHitByPitch();
        Integer ab  = stats.getAtBats();
        Integer sf  = stats.getSacrificeFlies();
        if (bb == null || hbp == null || ab == null || sf == null) return null;

        BigDecimal den = BigDecimal.valueOf((long) ab + bb + hbp + sf);
        if (den.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("[OBP] 분모 0 - playerId={}", stats.getPlayerId());
            return null;
        }
        return BigDecimal.valueOf((long) hits + bb + hbp).divide(den, SCALE_RATE, RM);
    }

    /** 장타율: TB / AB */
    public BigDecimal calculateSLG(BatterStats stats) {
        if (stats == null) return null;
        Integer tb = stats.getTotalBases();
        Integer ab = stats.getAtBats();
        if (tb == null || ab == null) return null;
        if (ab == 0) {
            log.warn("[SLG] 분모 0 - playerId={}", stats.getPlayerId());
            return null;
        }
        return BigDecimal.valueOf(tb).divide(BigDecimal.valueOf(ab), SCALE_RATE, RM);
    }

    /** OPS = OBP + SLG */
    public BigDecimal calculateOPS(BatterStats stats, Integer hits) {
        BigDecimal obp = calculateOBP(stats, hits);
        BigDecimal slg = calculateSLG(stats);
        if (obp == null || slg == null) return null;
        return obp.add(slg).setScale(SCALE_RATE, RM);
    }

    /** ISO (순수 장타력) = SLG - AVG */
    public BigDecimal calculateISO(BatterStats stats, BigDecimal battingAvg) {
        BigDecimal slg = calculateSLG(stats);
        if (slg == null || battingAvg == null) return null;
        return slg.subtract(battingAvg).setScale(SCALE_RATE, RM);
    }

    /** BB/K (선구안) = BB / SO */
    public BigDecimal calculateBBperK(BatterStats stats) {
        if (stats == null) return null;
        Integer bb = stats.getWalks();
        Integer so = stats.getStrikeouts();
        if (bb == null || so == null) return null;
        if (so == 0) {
            log.warn("[BB/K] 분모 0 - playerId={}", stats.getPlayerId());
            return null;
        }
        return BigDecimal.valueOf(bb).divide(BigDecimal.valueOf(so), SCALE_RATE, RM);
    }

    /** BABIP = (H - HR) / (AB - SO - HR + SF) */
    public BigDecimal calculateBABIP(BatterStats stats, Integer hits, Integer homeRuns) {
        if (stats == null || hits == null || homeRuns == null) return null;
        Integer ab = stats.getAtBats();
        Integer so = stats.getStrikeouts();
        Integer sf = stats.getSacrificeFlies();
        if (ab == null || so == null || sf == null) return null;

        int denInt = ab - so - homeRuns + sf;
        if (denInt <= 0) {
            log.warn("[BABIP] 분모 0 이하 - playerId={}", stats.getPlayerId());
            return null;
        }
        return BigDecimal.valueOf(hits - homeRuns)
                .divide(BigDecimal.valueOf(denInt), SCALE_RATE, RM);
    }

    /**
     * wOBA (가중 출루율) MLB 표준 가중치 사용
     * = (0.69*BB + 0.72*HBP + 0.89*1B + 1.27*2B + 1.62*3B + 2.10*HR)
     *   / (AB + BB - IBB + SF + HBP)
     * 단타(1B) = H - 2B - 3B - HR
     */
    public BigDecimal calculateWOBA(BatterStats stats, Integer hits, Integer homeRuns) {
        if (stats == null || hits == null || homeRuns == null) return null;
        Integer bb  = stats.getWalks();
        Integer ibb = stats.getIntentionalWalks();
        Integer hbp = stats.getHitByPitch();
        Integer b2  = stats.getDoubles();
        Integer b3  = stats.getTriples();
        Integer ab  = stats.getAtBats();
        Integer sf  = stats.getSacrificeFlies();
        if (bb == null || ibb == null || hbp == null || b2 == null
                || b3 == null || ab == null || sf == null) return null;

        int singles = hits - b2 - b3 - homeRuns;
        BigDecimal num = W_BB.multiply(BigDecimal.valueOf(bb))
                .add(W_HBP.multiply(BigDecimal.valueOf(hbp)))
                .add(W_1B.multiply(BigDecimal.valueOf(singles)))
                .add(W_2B.multiply(BigDecimal.valueOf(b2)))
                .add(W_3B.multiply(BigDecimal.valueOf(b3)))
                .add(W_HR.multiply(BigDecimal.valueOf(homeRuns)));

        int denInt = ab + bb - ibb + sf + hbp;
        if (denInt == 0) {
            log.warn("[wOBA] 분모 0 - playerId={}", stats.getPlayerId());
            return null;
        }
        return num.divide(BigDecimal.valueOf(denInt), SCALE_RATE, RM);
    }

    // ──────────────────────────────────────────────────────────────
    // 투수 지표 계산
    // ──────────────────────────────────────────────────────────────

    /**
     * WHIP = (BB + H) / IP = (BB + H) * 3 / outs
     * IP를 소수로 변환하면 1/3 이닝이 0.333...으로 잘려 반올림 오차가 생기므로
     * outs 정수를 분모로 직접 사용한다.
     */
    public BigDecimal calculateWHIP(PitcherStats stats) {
        if (stats == null) return null;
        Integer bb   = stats.getWalksAllowed();
        Integer h    = stats.getHitsAllowed();
        Integer outs = stats.getInningsOuts();
        if (bb == null || h == null || outs == null || outs == 0) return null;
        return BigDecimal.valueOf(((long) bb + h) * 3L)
                .divide(BigDecimal.valueOf(outs), SCALE_RATIO, RM);
    }

    /** K/9 = SO * 9 / IP = SO * 27 / outs */
    public BigDecimal calculateKper9(PitcherStats stats) {
        if (stats == null) return null;
        Integer so   = stats.getStrikeouts();
        Integer outs = stats.getInningsOuts();
        if (so == null || outs == null || outs == 0) return null;
        return BigDecimal.valueOf(so * 27L)
                .divide(BigDecimal.valueOf(outs), SCALE_RATIO, RM);
    }

    /** BB/9 = BB * 9 / IP = BB * 27 / outs */
    public BigDecimal calculateBBper9(PitcherStats stats) {
        if (stats == null) return null;
        Integer bb   = stats.getWalksAllowed();
        Integer outs = stats.getInningsOuts();
        if (bb == null || outs == null || outs == 0) return null;
        return BigDecimal.valueOf(bb * 27L)
                .divide(BigDecimal.valueOf(outs), SCALE_RATIO, RM);
    }

    /** K/BB = SO / BB */
    public BigDecimal calculateKperBB(PitcherStats stats) {
        if (stats == null) return null;
        Integer so = stats.getStrikeouts();
        Integer bb = stats.getWalksAllowed();
        if (so == null || bb == null) return null;
        if (bb == 0) {
            log.warn("[K/BB] 분모 0 - playerId={}", stats.getPlayerId());
            return null;
        }
        return BigDecimal.valueOf(so).divide(BigDecimal.valueOf(bb), SCALE_RATIO, RM);
    }

    /** HR/9 = HR * 9 / IP = HR * 27 / outs */
    public BigDecimal calculateHRper9(PitcherStats stats) {
        if (stats == null) return null;
        Integer hr   = stats.getHomeRunsAllowed();
        Integer outs = stats.getInningsOuts();
        if (hr == null || outs == null || outs == 0) return null;
        return BigDecimal.valueOf(hr * 27L)
                .divide(BigDecimal.valueOf(outs), SCALE_RATIO, RM);
    }

    /**
     * FIP = (13*HR + 3*(BB+HBP) - 2*SO) / IP + leagueConstant
     *     = (13*HR + 3*(BB+HBP) - 2*SO) * 3 / outs + leagueConstant
     */
    public BigDecimal calculateFIP(PitcherStats stats, BigDecimal leagueConstant) {
        if (stats == null || leagueConstant == null) return null;
        Integer hr   = stats.getHomeRunsAllowed();
        Integer bb   = stats.getWalksAllowed();
        Integer hbp  = stats.getHbpAllowed();
        Integer so   = stats.getStrikeouts();
        Integer outs = stats.getInningsOuts();
        if (hr == null || bb == null || hbp == null || so == null
                || outs == null || outs == 0) return null;

        BigDecimal num = BigDecimal.valueOf((13L * hr + 3L * (bb + hbp) - 2L * so) * 3L);
        return num.divide(BigDecimal.valueOf(outs), SCALE_RATIO + 4, RM)
                  .add(leagueConstant)
                  .setScale(SCALE_RATIO, RM);
    }

    // ──────────────────────────────────────────────────────────────
    // 검증 (사이트값 vs 계산값)
    // ──────────────────────────────────────────────────────────────

    /** OPS 검증 및 stat_validation_log 기록 */
    public boolean validateOPS(Long playerId, BatterStats stats, Integer hits) {
        return doValidate(playerId, "OPS", stats.getOps(), calculateOPS(stats, hits));
    }

    /** WHIP 검증 및 stat_validation_log 기록 */
    public boolean validateWHIP(Long playerId, PitcherStats stats) {
        return doValidate(playerId, "WHIP", stats.getWhip(), calculateWHIP(stats));
    }

    /** OBP 검증 및 stat_validation_log 기록 */
    public boolean validateOBP(Long playerId, BatterStats stats, Integer hits) {
        return doValidate(playerId, "OBP", stats.getOnBasePct(), calculateOBP(stats, hits));
    }

    /** SLG 검증 및 stat_validation_log 기록 */
    public boolean validateSLG(Long playerId, BatterStats stats) {
        return doValidate(playerId, "SLG", stats.getSluggingPct(), calculateSLG(stats));
    }

    private boolean doValidate(Long playerId, String metric,
                                BigDecimal siteValue, BigDecimal calculated) {
        // 어느 쪽이라도 null이면 비교 불가 → 로그 없이 true 반환
        if (calculated == null || siteValue == null) return true;

        BigDecimal diff    = calculated.subtract(siteValue).abs();
        boolean    isMatch = diff.compareTo(TOLERANCE) <= 0;

        statValidationLogMapper.insert(StatValidationLog.builder()
                .playerId(playerId)
                .metricName(metric)
                .siteValue(siteValue)
                .calculatedValue(calculated)
                .diff(diff)
                .isMatch(isMatch)
                .build());

        if (!isMatch) {
            log.warn("[검증] {} 불일치 player_id={}, 사이트={}, 계산={}, 차이={}",
                    metric, playerId, siteValue, calculated, diff);
        }
        return isMatch;
    }

    // ──────────────────────────────────────────────────────────────
    // 리그 평균 (Caffeine 캐시 5분 TTL)
    // ──────────────────────────────────────────────────────────────

    /** 리그 평균 OPS (저장된 사이트값 사용) */
    @Cacheable(value = "leagueStats", key = "'avgOPS'")
    public BigDecimal getLeagueAverageOPS() {
        List<BigDecimal> values = batterStatsMapper.findAll().stream()
                .map(BatterStats::getOps).filter(Objects::nonNull).toList();
        return calcAvg(values, SCALE_RATE);
    }

    /** 리그 평균 WHIP (저장된 사이트값 사용) */
    @Cacheable(value = "leagueStats", key = "'avgWHIP'")
    public BigDecimal getLeagueAverageWHIP() {
        List<BigDecimal> values = pitcherStatsMapper.findAll().stream()
                .map(PitcherStats::getWhip).filter(Objects::nonNull).toList();
        return calcAvg(values, SCALE_RATIO);
    }

    /** 리그 평균 ERA (earnedRuns/inningsOuts에서 직접 산출) */
    @Cacheable(value = "leagueStats", key = "'avgERA'")
    public BigDecimal getLeagueAverageERA() {
        long totalER = 0, totalOuts = 0;
        for (PitcherStats s : pitcherStatsMapper.findAll()) {
            if (s.getEarnedRuns() != null && s.getInningsOuts() != null && s.getInningsOuts() > 0) {
                totalER   += s.getEarnedRuns();
                totalOuts += s.getInningsOuts();
            }
        }
        if (totalOuts == 0) return BigDecimal.ZERO;
        // ERA = ER * 9 / IP = ER * 27 / inningsOuts (outs 단위)
        return BigDecimal.valueOf(totalER * 27L)
                .divide(BigDecimal.valueOf(totalOuts), SCALE_RATIO, RM);
    }

    /** 리그 평균 OBP (저장된 사이트값 사용) */
    @Cacheable(value = "leagueStats", key = "'avgOBP'")
    public BigDecimal getLeagueAverageOBP() {
        List<BigDecimal> values = batterStatsMapper.findAll().stream()
                .map(BatterStats::getOnBasePct).filter(Objects::nonNull).toList();
        return calcAvg(values, SCALE_RATE);
    }

    /** 리그 평균 SLG (저장된 사이트값 사용) */
    @Cacheable(value = "leagueStats", key = "'avgSLG'")
    public BigDecimal getLeagueAverageSLG() {
        List<BigDecimal> values = batterStatsMapper.findAll().stream()
                .map(BatterStats::getSluggingPct).filter(Objects::nonNull).toList();
        return calcAvg(values, SCALE_RATE);
    }

    // ── 타자 리그 평균 (추가 지표) ─────────────────────────────────

    /**
     * 리그 평균 ISO = (리그 TB - 리그 H) / 리그 AB
     * 집계 방식 → 소수 인원이 평균 왜곡하지 않도록
     */
    @Cacheable(value = "leagueStats", key = "'avgISO'")
    public BigDecimal getLeagueAverageISO() {
        BatterLeagueTotalsDto t = batterStatsMapper.findLeagueTotals();
        if (t.getTotalAtBats() == 0) return null;
        return BigDecimal.valueOf(t.getTotalTotalBases() - t.getTotalHits())
                .divide(BigDecimal.valueOf(t.getTotalAtBats()), SCALE_RATE, RM);
    }

    /** 리그 평균 BB/K = 리그 BB 합 / 리그 SO 합 */
    @Cacheable(value = "leagueStats", key = "'avgBBperK'")
    public BigDecimal getLeagueAverageBBperK() {
        BatterLeagueTotalsDto t = batterStatsMapper.findLeagueTotals();
        if (t.getTotalStrikeouts() == 0) return null;
        return BigDecimal.valueOf(t.getTotalWalks())
                .divide(BigDecimal.valueOf(t.getTotalStrikeouts()), SCALE_RATE, RM);
    }

    /**
     * 리그 평균 BABIP = (리그 H - 리그 HR) / (리그 AB - 리그 SO - 리그 HR + 리그 SF)
     */
    @Cacheable(value = "leagueStats", key = "'avgBABIP'")
    public BigDecimal getLeagueAverageBABIP() {
        BatterLeagueTotalsDto t = batterStatsMapper.findLeagueTotals();
        long den = t.getTotalAtBats() - t.getTotalStrikeouts()
                - t.getTotalHomeRuns() + t.getTotalSacrificeFlies();
        if (den <= 0) return null;
        return BigDecimal.valueOf(t.getTotalHits() - t.getTotalHomeRuns())
                .divide(BigDecimal.valueOf(den), SCALE_RATE, RM);
    }

    /**
     * 리그 평균 wOBA (MLB 표준 가중치, 집계 방식)
     * = (0.69*BB + 0.72*HBP + 0.89*1B + 1.27*2B + 1.62*3B + 2.10*HR)
     *   / (AB + BB - IBB + SF + HBP)
     */
    @Cacheable(value = "leagueStats", key = "'avgWOBA'")
    public BigDecimal getLeagueAverageWOBA() {
        BatterLeagueTotalsDto t = batterStatsMapper.findLeagueTotals();
        long singles = t.getTotalHits() - t.getTotalDoubles()
                - t.getTotalTriples() - t.getTotalHomeRuns();
        long den = t.getTotalAtBats() + t.getTotalWalks()
                - t.getTotalIntentionalWalks()
                + t.getTotalSacrificeFlies() + t.getTotalHitByPitch();
        if (den == 0) return null;
        BigDecimal num = W_BB.multiply(BigDecimal.valueOf(t.getTotalWalks()))
                .add(W_HBP.multiply(BigDecimal.valueOf(t.getTotalHitByPitch())))
                .add(W_1B.multiply(BigDecimal.valueOf(singles)))
                .add(W_2B.multiply(BigDecimal.valueOf(t.getTotalDoubles())))
                .add(W_3B.multiply(BigDecimal.valueOf(t.getTotalTriples())))
                .add(W_HR.multiply(BigDecimal.valueOf(t.getTotalHomeRuns())));
        return num.divide(BigDecimal.valueOf(den), SCALE_RATE, RM);
    }

    // ── 투수 리그 평균 (가중 평균 - outs 기반) ──────────────────────

    /** 리그 평균 K/9 = 리그 SO 합 * 27 / 리그 outs 합 */
    @Cacheable(value = "leagueStats", key = "'avgKper9'")
    public BigDecimal getLeagueAverageKper9() {
        LeagueTotalsDto t = pitcherStatsMapper.findLeagueTotals();
        if (t.getTotalOuts() == 0) return null;
        return BigDecimal.valueOf(t.getTotalStrikeouts() * 27L)
                .divide(BigDecimal.valueOf(t.getTotalOuts()), SCALE_RATIO, RM);
    }

    /** 리그 평균 BB/9 = 리그 BB 합 * 27 / 리그 outs 합 */
    @Cacheable(value = "leagueStats", key = "'avgBBper9'")
    public BigDecimal getLeagueAverageBBper9() {
        LeagueTotalsDto t = pitcherStatsMapper.findLeagueTotals();
        if (t.getTotalOuts() == 0) return null;
        return BigDecimal.valueOf(t.getTotalWalks() * 27L)
                .divide(BigDecimal.valueOf(t.getTotalOuts()), SCALE_RATIO, RM);
    }

    /** 리그 평균 HR/9 = 리그 HR 합 * 27 / 리그 outs 합 */
    @Cacheable(value = "leagueStats", key = "'avgHRper9'")
    public BigDecimal getLeagueAverageHRper9() {
        LeagueTotalsDto t = pitcherStatsMapper.findLeagueTotals();
        if (t.getTotalOuts() == 0) return null;
        return BigDecimal.valueOf(t.getTotalHomeRuns() * 27L)
                .divide(BigDecimal.valueOf(t.getTotalOuts()), SCALE_RATIO, RM);
    }

    /** 리그 평균 K/BB = 리그 SO 합 / 리그 BB 합 */
    @Cacheable(value = "leagueStats", key = "'avgKperBB'")
    public BigDecimal getLeagueAverageKperBB() {
        LeagueTotalsDto t = pitcherStatsMapper.findLeagueTotals();
        if (t.getTotalWalks() == 0) return null;
        return BigDecimal.valueOf(t.getTotalStrikeouts())
                .divide(BigDecimal.valueOf(t.getTotalWalks()), SCALE_RATIO, RM);
    }

    /**
     * FIP 리그 상수 = leagueAverageERA - 리그 FIP 성분(상수 제외)
     * FIP 성분 = (13*HR + 3*(BB+HBP) - 2*SO) / IP  의 가중 평균
     */
    @Cacheable(value = "leagueStats", key = "'fipConstant'")
    public BigDecimal getLeagueFIPConstant() {
        long totalER = 0, totalFIPNumerator = 0, totalOuts = 0;
        for (PitcherStats s : pitcherStatsMapper.findAll()) {
            if (s.getInningsOuts() == null || s.getInningsOuts() == 0) continue;
            if (s.getHomeRunsAllowed() == null || s.getWalksAllowed() == null
                    || s.getHbpAllowed() == null || s.getStrikeouts() == null
                    || s.getEarnedRuns() == null) continue;
            totalER           += s.getEarnedRuns();
            totalOuts         += s.getInningsOuts();
            totalFIPNumerator += 13L * s.getHomeRunsAllowed()
                    + 3L * (s.getWalksAllowed() + s.getHbpAllowed())
                    - 2L * s.getStrikeouts();
        }
        if (totalOuts == 0) return BigDecimal.ZERO;

        // leagueERA = totalER * 27 / totalOuts
        BigDecimal leagueERA = BigDecimal.valueOf(totalER * 27L)
                .divide(BigDecimal.valueOf(totalOuts), SCALE_RATIO + 4, RM);

        // leagueFIPComponent = totalFIPNumerator * 3 / totalOuts
        BigDecimal leagueFIPComp = BigDecimal.valueOf(totalFIPNumerator * 3L)
                .divide(BigDecimal.valueOf(totalOuts), SCALE_RATIO + 4, RM);

        return leagueERA.subtract(leagueFIPComp).setScale(SCALE_RATIO, RM);
    }

    // ──────────────────────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────────────────────

    /**
     * inningsOuts → 소수점 IP 변환 (표시 전용).
     * 계산에는 사용하지 말 것 — outs 정수를 분모로 직접 써야 반올림 오차가 없다.
     */
    public BigDecimal toIP(Integer inningsOuts) {
        if (inningsOuts == null || inningsOuts == 0) return null;
        return new BigDecimal(inningsOuts).divide(BigDecimal.valueOf(3), 1, RM);
    }

    private BigDecimal calcAvg(List<BigDecimal> values, int scale) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), scale, RM);
    }

    // ──────────────────────────────────────────────────────────────
    // 백분위 계산 (0~100, 100이 최상위)
    // ──────────────────────────────────────────────────────────────

    /**
     * 리그 내 순위 백분위: 정렬된 리스트에서 본인 등수 → (1 - rank/total) * 100
     * higherIsBetter=false 이면 낮을수록 좋은 지표 (ERA, WHIP 등) — 정렬 반전
     */
    int calculatePercentile(BigDecimal target, List<BigDecimal> all, boolean higherIsBetter) {
        if (target == null || all == null) return DEFAULT_PERCENTILE;
        List<BigDecimal> sorted = all.stream()
                .filter(Objects::nonNull)
                .sorted(higherIsBetter ? Comparator.reverseOrder() : Comparator.naturalOrder())
                .toList();
        if (sorted.isEmpty()) return DEFAULT_PERCENTILE;
        int rank = 0;
        for (BigDecimal v : sorted) {
            boolean isWorse = higherIsBetter ? target.compareTo(v) < 0 : target.compareTo(v) > 0;
            if (!isWorse) break;
            rank++;
        }
        return 100 - (rank * 100 / sorted.size());
    }

    // ── 리그 분포 리스트 (5분 캐시, self 참조로 AOP 프록시 경유) ──────

    @Cacheable(value = "league_distributions", key = "'batterAvg'")
    public List<BigDecimal> batterAvgDistribution() {
        return playerMapper.findAllByPlayerType(PlayerType.BATTER).stream()
                .map(p -> p.getBattingAvg() != null ? BigDecimal.valueOf(p.getBattingAvg()) : null)
                .filter(Objects::nonNull)
                .toList();
    }

    @Cacheable(value = "league_distributions", key = "'batterOps'")
    public List<BigDecimal> batterOpsDistribution() {
        return batterStatsMapper.findAll().stream()
                .map(BatterStats::getOps).filter(Objects::nonNull).toList();
    }

    @Cacheable(value = "league_distributions", key = "'batterObp'")
    public List<BigDecimal> batterObpDistribution() {
        return batterStatsMapper.findAll().stream()
                .map(BatterStats::getOnBasePct).filter(Objects::nonNull).toList();
    }

    @Cacheable(value = "league_distributions", key = "'batterIso'")
    public List<BigDecimal> batterIsoDistribution() {
        Map<Long, BigDecimal> avgByPlayer = playerMapper.findAllByPlayerType(PlayerType.BATTER).stream()
                .filter(p -> p.getBattingAvg() != null)
                .collect(Collectors.toMap(Player::getId, p -> BigDecimal.valueOf(p.getBattingAvg())));
        return batterStatsMapper.findAll().stream()
                .filter(s -> s.getSluggingPct() != null && avgByPlayer.containsKey(s.getPlayerId()))
                .map(s -> s.getSluggingPct().subtract(avgByPlayer.get(s.getPlayerId())).setScale(SCALE_RATE, RM))
                .toList();
    }

    @Cacheable(value = "league_distributions", key = "'batterBbk'")
    public List<BigDecimal> batterBbkDistribution() {
        return batterStatsMapper.findAll().stream()
                .filter(s -> s.getWalks() != null && s.getStrikeouts() != null && s.getStrikeouts() > 0)
                .map(s -> BigDecimal.valueOf(s.getWalks())
                        .divide(BigDecimal.valueOf(s.getStrikeouts()), SCALE_RATE, RM))
                .toList();
    }

    @Cacheable(value = "league_distributions", key = "'pitcherK9'")
    public List<BigDecimal> pitcherK9Distribution() {
        return pitcherStatsMapper.findAll().stream()
                .map(this::calculateKper9).filter(Objects::nonNull).toList();
    }

    @Cacheable(value = "league_distributions", key = "'pitcherBb9'")
    public List<BigDecimal> pitcherBb9Distribution() {
        return pitcherStatsMapper.findAll().stream()
                .map(this::calculateBBper9).filter(Objects::nonNull).toList();
    }

    @Cacheable(value = "league_distributions", key = "'pitcherOuts'")
    public List<BigDecimal> pitcherOutsDistribution() {
        return pitcherStatsMapper.findAll().stream()
                .filter(s -> s.getInningsOuts() != null)
                .map(s -> BigDecimal.valueOf(s.getInningsOuts()))
                .toList();
    }

    @Cacheable(value = "league_distributions", key = "'pitcherWhip'")
    public List<BigDecimal> pitcherWhipDistribution() {
        return pitcherStatsMapper.findAll().stream()
                .map(this::calculateWHIP).filter(Objects::nonNull).toList();
    }

    @Cacheable(value = "league_distributions", key = "'pitcherHr9'")
    public List<BigDecimal> pitcherHr9Distribution() {
        return pitcherStatsMapper.findAll().stream()
                .map(this::calculateHRper9).filter(Objects::nonNull).toList();
    }

    // ── 타자 백분위 ──────────────────────────────────────────────────

    public int getBatterPercentileAvg(BigDecimal avg) {
        return calculatePercentile(avg, self.batterAvgDistribution(), true);
    }

    public int getBatterPercentileOps(BigDecimal ops) {
        return calculatePercentile(ops, self.batterOpsDistribution(), true);
    }

    public int getBatterPercentileObp(BigDecimal obp) {
        return calculatePercentile(obp, self.batterObpDistribution(), true);
    }

    public int getBatterPercentileIso(BigDecimal iso) {
        return calculatePercentile(iso, self.batterIsoDistribution(), true);
    }

    public int getBatterPercentileBBperK(BigDecimal bbk) {
        return calculatePercentile(bbk, self.batterBbkDistribution(), true);
    }

    // ── 투수 백분위 ──────────────────────────────────────────────────

    public int getPitcherPercentileK9(BigDecimal k9) {
        return calculatePercentile(k9, self.pitcherK9Distribution(), true);
    }

    public int getPitcherPercentileBB9(BigDecimal bb9) {
        return calculatePercentile(bb9, self.pitcherBb9Distribution(), false);
    }

    public int getPitcherPercentileOuts(Integer outs) {
        if (outs == null) return DEFAULT_PERCENTILE;
        return calculatePercentile(BigDecimal.valueOf(outs), self.pitcherOutsDistribution(), true);
    }

    public int getPitcherPercentileWhip(BigDecimal whip) {
        return calculatePercentile(whip, self.pitcherWhipDistribution(), false);
    }

    public int getPitcherPercentileHR9(BigDecimal hr9) {
        return calculatePercentile(hr9, self.pitcherHr9Distribution(), false);
    }
}
