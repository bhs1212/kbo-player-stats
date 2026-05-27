package com.kbo.stats.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MatchupRecordDto {
    private Long      gameId;
    private LocalDate gameDate;
    private Integer   inning;
    private Integer   atBatOrder;
    private String    batterName;
    private String    pitcherName;
    private String    result;
    private String    resultCategory;
}
