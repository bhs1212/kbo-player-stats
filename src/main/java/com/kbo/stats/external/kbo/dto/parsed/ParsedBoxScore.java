package com.kbo.stats.external.kbo.dto.parsed;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ParsedBoxScore {
    ParsedGameMeta          meta;
    List<ParsedInningScore> inningScores;
    List<ParsedBatterLine>  awayBatters;
    List<ParsedBatterLine>  homeBatters;
    List<ParsedPitcherLine> awayPitchers;
    List<ParsedPitcherLine> homePitchers;
    List<ParsedGameEvent>   events;
}
