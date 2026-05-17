package com.kbo.stats.dto;

import com.kbo.stats.domain.Player;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "팀 주요 선수 리더 정보")
public class TeamLeadersDto {

    @Schema(description = "팀명")
    private String team;

    @Schema(description = "팀 타율 1위 선수")
    private Player battingLeader;

    @Schema(description = "팀 홈런 1위 선수")
    private Player homeRunLeader;

    @Schema(description = "팀 방어율 1위 선수 (ERA 최저)")
    private Player eraLeader;
}
