package com.kbo.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    private Long id;
    private String name;
    private String team;
    private String position;
    private PlayerType playerType;  // BATTER / PITCHER

    // 타자 기록
    private Double battingAvg;
    private Integer homeRuns;
    private Integer hits;
    private Integer atBats;          // 타수 (규정타석 판단용, sync 시 채워짐)
    private Integer rbi;
    private Integer stolenBases;

    // 투수 기록
    private Double era;
    private Integer inningsPitchedOuts;  // 아웃카운트 정수 (규정이닝 판단용, sync 시 채워짐)
    private Integer wins;
    private Integer saves;
    private Integer holds;

    // 공통
    private Integer games;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 세이버메트릭스 상세 기록 (JOIN 시에만 채워짐, 기본 null)
    private BatterStats batterStats;
    private PitcherStats pitcherStats;

    public boolean isBatter() {
        return PlayerType.BATTER == this.playerType;
    }

    public boolean isPitcher() {
        return PlayerType.PITCHER == this.playerType;
    }
}
