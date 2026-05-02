package com.kbo.stats.dto;

import lombok.Data;

@Data
public class TeamStatDto {
    private String team;
    private Integer totalHomeRuns;
    private Double avgBattingAvg;
    private Integer playerCount;
}
