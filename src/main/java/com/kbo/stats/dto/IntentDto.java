package com.kbo.stats.dto;

import com.kbo.stats.domain.PlayerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class IntentDto {

    @Schema(description = "선수 유형 (BATTER 또는 PITCHER)", example = "BATTER")
    private PlayerType playerType;

    @Schema(description = "팀명", example = "한화")
    private String team;

    @Schema(description = "특정 선수 이름", example = "이정후")
    private String playerName;

    @Schema(description = "정렬 기준 컬럼", example = "homeRuns",
            allowableValues = {"homeRuns", "battingAvg", "rbi", "stolenBases", "era", "wins", "saves", "holds"})
    private String sortBy;

    @Schema(description = "결과 최대 개수", example = "5")
    private Integer limit;
}
