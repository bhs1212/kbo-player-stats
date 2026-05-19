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
public class GameInningScore {
    private Long          id;
    private Long          gameId;
    private int           inning;
    private String        teamSide;   // "AWAY" | "HOME"
    private Integer       score;      // null = 미진행/연장 미사용
    private LocalDateTime createdAt;
}
