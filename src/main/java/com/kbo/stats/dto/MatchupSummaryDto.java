package com.kbo.stats.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class MatchupSummaryDto {
    private String  batterName;
    private String  pitcherName;
    private Integer plateAppearances;
    private Integer atBats;
    private Integer hits;
    private Integer homeRuns;
    private Integer walks;
    private Integer strikeouts;
    private BigDecimal avg;
    private BigDecimal obp;
    private List<MatchupRecordDto> records;
}
