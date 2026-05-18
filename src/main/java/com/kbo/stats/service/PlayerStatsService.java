package com.kbo.stats.service;

import com.kbo.stats.domain.BatterStats;
import com.kbo.stats.domain.PitcherStats;
import com.kbo.stats.mapper.BatterStatsMapper;
import com.kbo.stats.mapper.PitcherStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerStatsService {

    private final BatterStatsMapper batterStatsMapper;
    private final PitcherStatsMapper pitcherStatsMapper;

    // ── 조회 ──────────────────────────────────────────────────────

    public BatterStats getBatterStats(Long playerId) {
        return batterStatsMapper.findByPlayerId(playerId);
    }

    public PitcherStats getPitcherStats(Long playerId) {
        return pitcherStatsMapper.findByPlayerId(playerId);
    }

    // 리그 평균 계산용 전체 조회
    public List<BatterStats> getAllBatterStats() {
        return batterStatsMapper.findAll();
    }

    public List<PitcherStats> getAllPitcherStats() {
        return pitcherStatsMapper.findAll();
    }

    // ── 저장 ──────────────────────────────────────────────────────

    @Transactional
    public void upsertBatterStats(BatterStats stats) {
        batterStatsMapper.upsert(stats);
        log.debug("타자 세이버메트릭스 upsert: playerId={}", stats.getPlayerId());
    }

    @Transactional
    public void upsertPitcherStats(PitcherStats stats) {
        pitcherStatsMapper.upsert(stats);
        log.debug("투수 세이버메트릭스 upsert: playerId={}", stats.getPlayerId());
    }

    // 크롤링 재실행 전 전체 초기화
    @Transactional
    public void deleteAll() {
        batterStatsMapper.deleteAll();
        pitcherStatsMapper.deleteAll();
        log.info("세이버메트릭스 통계 전체 삭제 완료");
    }

    // ── 세이버메트릭스 계산 ────────────────────────────────────────
    // 크롤링 후 사이트 값과 직접 계산 값을 비교(검증)할 때 사용

    /**
     * OBP (출루율) = (H + BB + HBP) / (AB + BB + HBP + SF)
     * 희생번트(SAC)는 분모에서 제외, 희생플라이(SF)는 포함
     */
    public BigDecimal calcObp(Integer hits, Integer walks, Integer hbp,
                               Integer atBats, Integer sacrificeFlies) {
        int h   = safe(hits);
        int bb  = safe(walks);
        int hbpV = safe(hbp);
        int ab  = safe(atBats);
        int sf  = safe(sacrificeFlies);

        int denom = ab + bb + hbpV + sf;
        if (denom == 0) return BigDecimal.ZERO;

        return BigDecimal.valueOf(h + bb + hbpV)
                .divide(BigDecimal.valueOf(denom), 3, RoundingMode.HALF_UP);
    }

    /**
     * SLG (장타율) = TB / AB
     */
    public BigDecimal calcSlg(Integer totalBases, Integer atBats) {
        int ab = safe(atBats);
        if (ab == 0) return BigDecimal.ZERO;

        return BigDecimal.valueOf(safe(totalBases))
                .divide(BigDecimal.valueOf(ab), 3, RoundingMode.HALF_UP);
    }

    /**
     * OPS = OBP + SLG
     */
    public BigDecimal calcOps(BigDecimal obp, BigDecimal slg) {
        if (obp == null || slg == null) return null;
        return obp.add(slg).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * WHIP = (BB + H) / IP  (IP = inningsOuts / 3.0, 소수점 2자리)
     */
    public BigDecimal calcWhip(Integer walksAllowed, Integer hitsAllowed, Integer inningsOuts) {
        if (inningsOuts == null || inningsOuts == 0) return null;

        BigDecimal ip = BigDecimal.valueOf(inningsOuts)
                .divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
        int numerator = safe(walksAllowed) + safe(hitsAllowed);

        return BigDecimal.valueOf(numerator)
                .divide(ip, 2, RoundingMode.HALF_UP);
    }

    /**
     * BatterStats 로부터 OBP/SLG/OPS를 계산해서 새 객체로 반환.
     * hits는 player.hits 컬럼에서 가져와야 하므로 별도 파라미터로 받음.
     * 크롤링 서비스에서 사이트값 검증 또는 빈 값 보완에 활용.
     */
    public BatterStats fillCalculatedStats(BatterStats raw, Integer hits) {
        BigDecimal obp = calcObp(hits, raw.getWalks(), raw.getHitByPitch(),
                raw.getAtBats(), raw.getSacrificeFlies());
        BigDecimal slg = calcSlg(raw.getTotalBases(), raw.getAtBats());
        BigDecimal ops = calcOps(obp, slg);

        return BatterStats.builder()
                .playerId(raw.getPlayerId())
                .plateAppearances(raw.getPlateAppearances())
                .atBats(raw.getAtBats())
                .runs(raw.getRuns())
                .doubles(raw.getDoubles())
                .triples(raw.getTriples())
                .totalBases(raw.getTotalBases())
                .walks(raw.getWalks())
                .intentionalWalks(raw.getIntentionalWalks())
                .hitByPitch(raw.getHitByPitch())
                .strikeouts(raw.getStrikeouts())
                .doublePlays(raw.getDoublePlays())
                .sacrificeHits(raw.getSacrificeHits())
                .sacrificeFlies(raw.getSacrificeFlies())
                .sluggingPct(slg)
                .onBasePct(obp)
                .ops(ops)
                .build();
    }

    /**
     * PitcherStats 로부터 WHIP를 계산해서 새 객체로 반환.
     */
    public PitcherStats fillCalculatedStats(PitcherStats raw) {
        BigDecimal whip = calcWhip(raw.getWalksAllowed(), raw.getHitsAllowed(), raw.getInningsOuts());

        return PitcherStats.builder()
                .playerId(raw.getPlayerId())
                .losses(raw.getLosses())
                .winPct(raw.getWinPct())
                .inningsOuts(raw.getInningsOuts())
                .hitsAllowed(raw.getHitsAllowed())
                .homeRunsAllowed(raw.getHomeRunsAllowed())
                .walksAllowed(raw.getWalksAllowed())
                .hbpAllowed(raw.getHbpAllowed())
                .strikeouts(raw.getStrikeouts())
                .runsAllowed(raw.getRunsAllowed())
                .earnedRuns(raw.getEarnedRuns())
                .whip(whip)
                .completeGames(raw.getCompleteGames())
                .shutouts(raw.getShutouts())
                .qualityStarts(raw.getQualityStarts())
                .blownSaves(raw.getBlownSaves())
                .battersFaced(raw.getBattersFaced())
                .pitchesThrown(raw.getPitchesThrown())
                .opponentAvg(raw.getOpponentAvg())
                .wildPitches(raw.getWildPitches())
                .balks(raw.getBalks())
                .build();
    }

    // null-safe 정수 변환
    private int safe(Integer v) {
        return v == null ? 0 : v;
    }
}
