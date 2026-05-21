package com.kbo.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatterBoxScoreAggregate {
    private Long    playerId;
    private Integer totalAtBats;
    private Integer totalHits;
    private Integer gameCount;
}
