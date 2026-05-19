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
public class GamePitcherLog {
    private Long          id;
    private Long          gameId;
    private String        teamSide;           // "AWAY" | "HOME"
    private int           pitchOrder;         // 1 = 선발
    private String        playerName;
    private String        appearanceLabel;
    private String        result;             // WIN | LOSE | SAVE | HOLD | NONE
    private int           seasonW;
    private int           seasonL;
    private int           seasonS;
    private int           inningsPitchedOuts; // 아웃카운트 정수 (6.2이닝=20)
    private int           battersFaced;
    private int           pitches;
    private int           atBatsAgainst;
    private int           hitsAgainst;
    private int           homeRunsAgainst;
    private int           walksHbp;
    private int           strikeouts;
    private int           runsAllowed;
    private int           earnedRuns;
    private BigDecimal    seasonEra;
    private LocalDateTime createdAt;
}
