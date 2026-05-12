package com.kbo.stats.dto;

import com.kbo.stats.domain.PlayerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PlayerSearchDto {

    @Schema(description = "선수명 (부분 검색)", example = "이정")
    private String name;

    @Schema(description = "팀명", example = "한화")
    private String team;

    @Schema(description = "포지션", example = "외야수")
    private String position;

    @Schema(description = "선수 유형 (BATTER / PITCHER)")
    private PlayerType playerType;

    @Schema(description = "페이지 번호 (1부터 시작)", example = "1")
    private int page = 1;

    @Schema(description = "페이지당 항목 수", example = "20")
    private int size = 20;

    public int getOffset() {
        return (page - 1) * size;
    }
}
