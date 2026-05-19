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
public class GameEvent {
    private Long          id;
    private Long          gameId;
    private String        eventType;       // HOME_RUN | TRIPLE | DOUBLE | ERROR | DOUBLE_PLAY | WILD_PITCH
    private String        playerName;
    private Integer       inning;
    private Integer       seasonCount;     // 홈런 시즌 N호
    private Integer       runs;            // 홈런 N점
    private String        opponentPitcher; // 홈런 상대 투수
    private String        rawText;
    private LocalDateTime createdAt;
}
