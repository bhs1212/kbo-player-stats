package com.kbo.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequestDto {

    @Schema(description = "사용자의 자연어 질문", example = "올해 홈런 1위 누구야?", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "질문을 입력해주세요")
    private String question;
}
