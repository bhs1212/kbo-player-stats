package com.kbo.stats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ClaudeApiClient {

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public ClaudeApiClient(
            WebClient.Builder webClientBuilder,
            @Value("${claude.api.key}") String apiKey,
            @Value("${claude.api.base-url}") String baseUrl,
            @Value("${claude.api.model}") String model,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Tool Use 지원 Claude Messages API 호출 — 응답 전체를 JsonNode로 반환
     */
    public JsonNode completeWithTools(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools) {

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 1024,
                "system", systemPrompt,
                "tools", tools,
                "messages", messages
        );

        try {
            String responseBody = webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            int inputTokens  = root.path("usage").path("input_tokens").asInt();
            int outputTokens = root.path("usage").path("output_tokens").asInt();
            log.debug("Tool Use API 응답 - stop_reason: {}, 입력 토큰: {}, 출력 토큰: {}",
                    root.path("stop_reason").asText(), inputTokens, outputTokens);
            return root;
        } catch (Exception e) {
            log.error("Claude Tool Use API 호출 실패", e);
            throw new IllegalStateException("Claude API 호출 중 오류가 발생했습니다", e);
        }
    }

    /**
     * Claude Messages API 호출 후 텍스트 응답 반환
     */
    public String complete(String systemPrompt, String userMessage) {
        log.debug("Claude API 호출 - 시스템 프롬프트 {}자, 사용자 메시지 {}자",
                systemPrompt.length(), userMessage.length());

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 1024,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        try {
            String responseBody = webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("content").get(0).path("text").asText();
            int inputTokens = root.path("usage").path("input_tokens").asInt();
            int outputTokens = root.path("usage").path("output_tokens").asInt();
            log.debug("Claude API 응답 - {}자, 입력 토큰: {}, 출력 토큰: {}",
                    text.length(), inputTokens, outputTokens);
            return text;
        } catch (Exception e) {
            log.error("Claude API 호출 실패", e);
            throw new IllegalStateException("Claude API 호출 중 오류가 발생했습니다", e);
        }
    }
}
