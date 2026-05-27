package com.kbo.stats.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** GameMatchupMapper 조회 결과 내부 매핑용 (JOIN game 결과) */
@Getter
@Setter
@NoArgsConstructor
public class MatchupRecordRow {
    private Long      gameId;
    private LocalDate gameDate;
    private Integer   inning;
    private Integer   atBatOrder;
    private String    batterName;
    private String    pitcherName;
    private String    result;
}
