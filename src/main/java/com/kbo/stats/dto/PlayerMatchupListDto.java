package com.kbo.stats.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlayerMatchupListDto {
    private String playerName;
    private String playerType;  // "BATTER" or "PITCHER"
    private List<MatchupSummaryDto> matchups;
}
