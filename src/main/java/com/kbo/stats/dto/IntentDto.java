package com.kbo.stats.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kbo.stats.domain.PlayerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
public class IntentDto {

    @Schema(description = "의도 유형", example = "PLAYER_STATS",
            allowableValues = {"PLAYER_STATS", "SCHEDULE_QUERY", "RANKING", "PLAYER_LIST"})
    private String intentType;

    @Schema(description = "경기 조회 시작 날짜", example = "2026-06-06")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Schema(description = "경기 조회 종료 날짜", example = "2026-06-07")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;

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

    @Schema(description = "강조할 특정 통계 필드 (선수 질문 시)", example = "stolenBases",
            allowableValues = {"homeRuns", "battingAvg", "rbi", "stolenBases", "era", "wins", "saves", "holds"})
    private String statField;
}
