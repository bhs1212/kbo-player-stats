package com.kbo.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PitcherBoxScoreAggregate {
    private Long    playerId;
    private Integer totalInningsOuts;
    private Integer totalEarnedRuns;
    private Integer totalWalksHbp;
    private Integer totalHitsAgainst;
    private Integer gameCount;
}
