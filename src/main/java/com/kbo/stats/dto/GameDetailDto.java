package com.kbo.stats.dto;

import com.kbo.stats.domain.*;
import lombok.*;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameDetailDto {

    private Game game;
    private boolean hasBoxScore;
    private GameBoxScore boxScore;

    @Builder.Default
    private List<GameInningScore> innings = Collections.emptyList();

    @Builder.Default
    private List<GameEvent> events = Collections.emptyList();

    @Builder.Default
    private List<GameBatterLog> awayBatters = Collections.emptyList();

    @Builder.Default
    private List<GameBatterLog> homeBatters = Collections.emptyList();

    @Builder.Default
    private List<GamePitcherLog> awayPitchers = Collections.emptyList();

    @Builder.Default
    private List<GamePitcherLog> homePitchers = Collections.emptyList();
}
