package com.kbo.stats.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 타자 리그 합계 집계 결과 (BatterStatsMapper.findLeagueTotals 반환용, player JOIN 포함) */
@Getter
@Setter
@NoArgsConstructor
public class BatterLeagueTotalsDto {
    private Long totalAtBats;
    private Long totalHits;
    private Long totalHomeRuns;
    private Long totalDoubles;
    private Long totalTriples;
    private Long totalTotalBases;
    private Long totalWalks;
    private Long totalIntentionalWalks;
    private Long totalHitByPitch;
    private Long totalStrikeouts;
    private Long totalSacrificeFlies;
}
