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
public class GameMatchup {
    private Long id;
    private Long gameId;
    private Integer inning;
    private Integer atBatOrder;
    private String batterTeamSide;
    private String batterName;
    private Long batterPlayerId;
    private Integer batterBattingOrder;
    private String pitcherName;
    private Long pitcherPlayerId;
    private Integer pitcherPitchOrder;
    private String result;
    private LocalDateTime createdAt;
}
