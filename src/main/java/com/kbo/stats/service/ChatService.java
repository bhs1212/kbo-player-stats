package com.kbo.stats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.*;
import com.kbo.stats.mapper.PlayerMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ClaudeApiClient claudeApiClient;
    private final PlayerService playerService;
    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private String intentExtractionPrompt;
    private String answerGenerationPrompt;

    @PostConstruct
    public void loadPrompts() throws IOException {
        intentExtractionPrompt = loadResource("classpath:prompts/intent-extraction.txt");
        answerGenerationPrompt = loadResource("classpath:prompts/answer-generation.txt");
        log.info("프롬프트 로드 완료 - 의도 추출 {}자, 답변 생성 {}자",
                intentExtractionPrompt.length(), answerGenerationPrompt.length());
    }

    private String loadResource(String path) throws IOException {
        try (InputStream is = resourceLoader.getResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // 질문을 소문자+trim 정규화하여 캐시 키로 사용 → 공백·대소문자 차이 무시
    @Cacheable(value = "chatAnswers", key = "#question.toLowerCase().trim()")
    public ChatResponseDto answer(String question) {
        log.info("LLM 호출 시작 (캐시 미스): question={}", question);

        // 1단계: 의도 추출
        log.debug("의도 추출 시작 - 질문: {}", question);
        String intentJson = claudeApiClient.complete(intentExtractionPrompt, question);
        intentJson = stripJsonCodeBlock(intentJson);

        IntentDto intent;
        try {
            intent = objectMapper.readValue(intentJson, IntentDto.class);
            log.debug("의도 추출 완료 - playerType={}, team={}, playerName={}, sortBy={}, limit={}",
                    intent.getPlayerType(), intent.getTeam(), intent.getPlayerName(),
                    intent.getSortBy(), intent.getLimit());
        } catch (Exception e) {
            log.error("의도 JSON 파싱 실패: {}", intentJson, e);
            throw new IllegalStateException("의도 파싱에 실패했습니다");
        }

        // 2단계: 의도 기반 DB 조회
        int limit = intent.getLimit() != null ? intent.getLimit() : 5;
        List<Player> players = fetchPlayers(intent, limit);
        log.debug("데이터 조회 완료 - {}명", players.size());

        // 3단계: 답변 생성
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(players);
        } catch (Exception e) {
            throw new IllegalStateException("데이터 직렬화 실패", e);
        }

        String userMessage = "사용자 질문: " + question + "\n\n조회 데이터:\n" + dataJson;
        String answer = claudeApiClient.complete(answerGenerationPrompt, userMessage);
        log.debug("답변 생성 완료 - {}자", answer.length());

        return ChatResponseDto.builder()
                .answer(answer)
                .sources(players)
                .intent(intent)
                .build();
    }

    private List<Player> fetchPlayers(IntentDto intent, int limit) {
        // 특정 선수 이름 검색
        if (intent.getPlayerName() != null && !intent.getPlayerName().isBlank()) {
            PlayerSearchDto searchDto = new PlayerSearchDto();
            searchDto.setName(intent.getPlayerName());
            searchDto.setSize(limit);
            return playerMapper.findAll(searchDto);
        }

        // sortBy 기반 랭킹 조회
        if (intent.getSortBy() != null) {
            return fetchRanking(intent.getSortBy(), limit, intent);
        }

        // 팀 필터 검색
        if (intent.getTeam() != null && !intent.getTeam().isBlank()) {
            PlayerSearchDto searchDto = new PlayerSearchDto();
            searchDto.setTeam(intent.getTeam());
            searchDto.setPlayerType(intent.getPlayerType());
            searchDto.setSize(limit);
            return playerMapper.findAll(searchDto);
        }

        // 전체에서 limit개 반환
        PlayerSearchDto searchDto = new PlayerSearchDto();
        searchDto.setPlayerType(intent.getPlayerType());
        searchDto.setSize(limit);
        return playerMapper.findAll(searchDto);
    }

    private List<Player> fetchRanking(String sortBy, int limit, IntentDto intent) {
        return switch (sortBy) {
            case "homeRuns" -> playerService.getHomeRunRanking(limit);
            case "battingAvg" -> playerService.getBattingRanking(limit);
            case "rbi" -> playerService.getRbiRanking(limit);
            case "stolenBases" -> playerService.getStolenBasesRanking(limit);
            case "era" -> playerService.getEraRanking(limit);
            case "wins" -> playerService.getWinsRanking(limit);
            case "saves" -> playerService.getSavesRanking(limit);
            case "holds" -> playerService.getHoldsRanking(limit);
            default -> {
                PlayerSearchDto searchDto = new PlayerSearchDto();
                searchDto.setPlayerType(intent.getPlayerType());
                searchDto.setSize(limit);
                yield playerMapper.findAll(searchDto);
            }
        };
    }

    // 마크다운 코드블록(```json ... ```) 제거
    private String stripJsonCodeBlock(String text) {
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
}
