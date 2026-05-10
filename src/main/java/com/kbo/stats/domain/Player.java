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
    private Integer rbi;
    private Integer stolenBases;

    // 투수 기록
    private Double era;
    private Integer wins;
    private Integer saves;
    private Integer holds;

    // 공통
    private Integer games;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isBatter() {
        return PlayerType.BATTER == this.playerType;
    }

    public boolean isPitcher() {
        return PlayerType.PITCHER == this.playerType;
    }
}
