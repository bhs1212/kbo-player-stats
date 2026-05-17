package com.kbo.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "팀 순위 정보")
public class TeamStandingDto {

    @Schema(description = "팀명")
    private String team;

    @Schema(description = "승")
    private int wins;

    @Schema(description = "패")
    private int losses;

    @Schema(description = "무승부")
    private int draws;

    @Schema(description = "총 경기 수")
    private int totalGames;

    @Schema(description = "승률")
    private double winRate;

    @Schema(description = "순위 (1부터 시작)")
    private int rank;
}
