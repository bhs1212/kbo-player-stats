package com.kbo.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatValidationLog {

    private Long id;
    private Long playerId;
    private String metricName;      // OPS / WHIP / OBP / SLG 등
    private BigDecimal siteValue;   // KBO 사이트 제공값
    private BigDecimal calculatedValue; // 직접 계산값
    private BigDecimal diff;        // |siteValue - calculatedValue|
    private Boolean isMatch;        // 0.001 이내 일치 여부
    private LocalDateTime crawledAt;
}
