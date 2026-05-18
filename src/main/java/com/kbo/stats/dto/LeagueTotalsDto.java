package com.kbo.stats.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 투수 리그 합계 집계 결과 (PitcherStatsMapper.findLeagueTotals 반환용) */
@Getter
@Setter
@NoArgsConstructor
public class LeagueTotalsDto {
    private Long totalOuts;
    private Long totalStrikeouts;
    private Long totalWalks;
    private Long totalHomeRuns;
    private Long totalHitsAllowed;
    private Long totalEarnedRuns;
}
