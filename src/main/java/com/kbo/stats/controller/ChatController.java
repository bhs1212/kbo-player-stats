package com.kbo.stats.controller;

import com.kbo.stats.dto.ApiResponse;
import com.kbo.stats.dto.ChatRequestDto;
import com.kbo.stats.dto.ChatResponseDto;
import com.kbo.stats.service.ChatService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "챗봇 API", description = "자연어 질문 처리 (Claude API + RAG)")
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Hidden
    @GetMapping("/chat")
    public String chatPage() {
        return "chat/index";
    }

    @Operation(
            summary = "자연어 질문 답변",
            description = "사용자의 자연어 질문을 받아 Claude API로 의도 분석 후, 그 의도에 맞는 선수 데이터를 DB에서 조회하여 자연어 답변을 생성합니다. 2단계 LLM 호출 구조 (의도 추출 → 답변 생성)"
    )
    @PostMapping("/api/chat")
    @ResponseBody
    public ApiResponse<ChatResponseDto> chat(@Valid @RequestBody ChatRequestDto request) {
        log.debug("챗봇 질문 수신: {}", request.getQuestion());
        ChatResponseDto response = chatService.answer(request.getQuestion());
        return ApiResponse.ok(response);
    }
}
