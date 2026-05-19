package com.kbo.stats.external.kbo.dto.parsed;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ParsedGameMeta {
    String kboGameId;
    String gameDate;
    int    leId;
    int    srId;
    int    seasonId;
    String homeTeamId;
    String homeTeamName;
    String awayTeamId;
    String awayTeamName;
    String stadium;
    int    crowdCount;
    String startTime;
    String endTime;
    String useTime;
    int    awayScore;
    int    homeScore;
    int    maxInning;
    int    realMaxInning;
    int    homeSeasonW;
    int    homeSeasonL;
    int    homeSeasonD;
    int    awaySeasonW;
    int    awaySeasonL;
    int    awaySeasonD;
    int    homeHits;
    int    homeErrors;
    int    homeWalks;
    int    awayHits;
    int    awayErrors;
    int    awayWalks;
    String finishingHit;
    String umpires;
}
