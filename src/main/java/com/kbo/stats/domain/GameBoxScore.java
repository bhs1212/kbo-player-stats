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
public class GameBoxScore {
    private Long          id;
    private Long          gameId;
    private int           leId;
    private int           srId;
    private int           seasonId;
    private Integer       crowdCn;
    private String        startTm;
    private String        endTm;
    private String        useTm;
    private Integer       maxInning;
    private Integer       realMaxInning;
    private Integer       homeSeasonW;
    private Integer       homeSeasonL;
    private Integer       homeSeasonD;
    private Integer       awaySeasonW;
    private Integer       awaySeasonL;
    private Integer       awaySeasonD;
    private Integer       homeHits;
    private Integer       homeErrors;
    private Integer       homeWalks;
    private Integer       awayHits;
    private Integer       awayErrors;
    private Integer       awayWalks;
    private String        finishingHit;
    private String        umpires;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
