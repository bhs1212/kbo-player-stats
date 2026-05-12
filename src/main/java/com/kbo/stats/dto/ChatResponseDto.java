package com.kbo.stats.dto;

import com.kbo.stats.domain.Player;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatResponseDto {

    @Schema(description = "Claude API가 생성한 자연어 답변")
    private String answer;

    @Schema(description = "답변 근거가 된 선수 데이터 목록")
    private List<Player> sources;

    @Schema(description = "1단계에서 추출된 의도 (디버깅 참고용)")
    private IntentDto intent;
}
