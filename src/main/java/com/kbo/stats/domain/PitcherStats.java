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
public class PitcherStats {

    private Long playerId;

    // 원시 데이터 (KBO Basic1)
    private Integer losses;             // L, 패
    private BigDecimal winPct;          // WPCT, 승률
    private Integer inningsOuts;        // IP를 outs로 환산 (52⅓이닝 = 157 outs)
    private Integer hitsAllowed;        // H, 피안타
    private Integer homeRunsAllowed;    // HR, 피홈런
    private Integer walksAllowed;       // BB, 볼넷 허용
    private Integer hbpAllowed;         // HBP, 사구 허용
    private Integer strikeouts;         // SO, 탈삼진
    private Integer runsAllowed;        // R, 실점
    private Integer earnedRuns;         // ER, 자책점

    // 사이트 계산값 (검증용으로 저장)
    private BigDecimal whip;            // WHIP

    // Basic2 보조 지표
    private Integer completeGames;      // CG, 완투
    private Integer shutouts;           // SHO, 완봉
    private Integer qualityStarts;      // QS, 퀄리티스타트
    private Integer blownSaves;         // BSV, 블론세이브
    private Integer battersFaced;       // TBF, 상대 타자수
    private Integer pitchesThrown;      // NP, 투구수
    private BigDecimal opponentAvg;     // 피안타율
    private Integer wildPitches;        // WP, 폭투
    private Integer balks;              // BK, 보크

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ⅓이닝 단위로 저장된 inningsOuts를 "52⅓" 형식으로 반환
    public String getInningsDisplay() {
        if (inningsOuts == null) return null;
        int innings = inningsOuts / 3;
        int outs = inningsOuts % 3;
        if (outs == 0) return String.valueOf(innings);
        return innings + (outs == 1 ? "⅓" : "⅔");
    }
}
