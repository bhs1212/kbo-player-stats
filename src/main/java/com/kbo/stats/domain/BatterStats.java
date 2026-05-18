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
public class BatterStats {

    private Long playerId;

    // 원시 데이터 (KBO Basic1 + Basic2)
    private Integer plateAppearances;   // PA, 타석
    private Integer atBats;             // AB, 타수
    private Integer runs;               // R, 득점
    private Integer doubles;            // 2B, 2루타
    private Integer triples;            // 3B, 3루타
    private Integer totalBases;         // TB, 루타 합계
    private Integer walks;              // BB, 볼넷
    private Integer intentionalWalks;   // IBB, 고의사구
    private Integer hitByPitch;         // HBP, 사구
    private Integer strikeouts;         // SO, 삼진
    private Integer doublePlays;        // GDP, 병살타
    private Integer sacrificeHits;      // SAC, 희생번트
    private Integer sacrificeFlies;     // SF, 희생플라이

    // 사이트 계산값 (검증용으로 저장)
    private BigDecimal sluggingPct;     // SLG, 장타율
    private BigDecimal onBasePct;       // OBP, 출루율
    private BigDecimal ops;             // OPS

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
