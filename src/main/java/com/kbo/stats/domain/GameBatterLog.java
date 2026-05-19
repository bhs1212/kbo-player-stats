package com.kbo.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameBatterLog {
    private Long          id;
    private Long          gameId;
    private String        teamSide;       // "AWAY" | "HOME"
    private int           battingOrder;
    private String        position;
    private String        playerName;
    private int           atBats;
    private int           hits;
    private int           rbi;
    private int           walks;
    private BigDecimal    seasonAvg;
    private String        inningResults;  // JSON array 문자열 (서비스 레이어에서 직렬화)
    private LocalDateTime createdAt;
}
