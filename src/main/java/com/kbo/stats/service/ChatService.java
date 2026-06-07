package com.kbo.stats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.*;
import com.kbo.stats.mapper.GameMapper;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ClaudeApiClient claudeApiClient;
    private final PlayerService playerService;
    private final PlayerMapper playerMapper;
    private final GameMapper gameMapper;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private String intentExtractionPrompt;
    private String answerGenerationPrompt;

    private static final Map<String, String> TEAM_ALIASES = Map.ofEntries(
        Map.entry("KIA 타이거즈", "KIA"), Map.entry("기아 타이거즈", "KIA"),
        Map.entry("기아", "KIA"), Map.entry("타이거즈", "KIA"),
        Map.entry("한화 이글스", "한화"), Map.entry("이글스", "한화"),
        Map.entry("LG 트윈스", "LG"), Map.entry("엘지 트윈스", "LG"),
        Map.entry("엘지", "LG"), Map.entry("트윈스", "LG"),
        Map.entry("두산 베어스", "두산"), Map.entry("베어스", "두산"),
        Map.entry("삼성 라이온즈", "삼성"), Map.entry("라이온즈", "삼성"),
        Map.entry("롯데 자이언츠", "롯데"), Map.entry("자이언츠", "롯데"),
        Map.entry("SSG 랜더스", "SSG"), Map.entry("랜더스", "SSG"), Map.entry("쓱", "SSG"),
        Map.entry("NC 다이노스", "NC"), Map.entry("다이노스", "NC"), Map.entry("엔씨", "NC"),
        Map.entry("KT 위즈", "KT"), Map.entry("케이티 위즈", "KT"),
        Map.entry("케이티", "KT"), Map.entry("위즈", "KT"),
        Map.entry("키움 히어로즈", "키움"), Map.entry("히어로즈", "키움")
    );

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

    @Cacheable(value = "chatAnswers", key = "#question.toLowerCase().trim()")
    public ChatResponseDto answer(String question) {
        log.info("LLM 호출 시작 (캐시 미스): question={}", question);

        // 전처리: 상대 날짜 → 절대 날짜, 팀명 동의어 → 정규화
        String preprocessed = preprocessQuestion(question);
        String normalizedInput = normalizeTeamNames(preprocessed);
        log.debug("원본: {} | 전처리: {} | 정규화: {}", question, preprocessed, normalizedInput);

        String timeContext = buildTimeContext();

        // 1단계: 의도 추출
        String intentJson = claudeApiClient.complete(
            timeContext + "\n" + intentExtractionPrompt, normalizedInput);
        intentJson = stripJsonCodeBlock(intentJson);

        IntentDto intent;
        try {
            intent = objectMapper.readValue(intentJson, IntentDto.class);
            log.debug("의도 추출 완료 - intentType={}, team={}, playerName={}, sortBy={}, " +
                    "statField={}, limit={}, startDate={}, endDate={}",
                    intent.getIntentType(), intent.getTeam(), intent.getPlayerName(),
                    intent.getSortBy(), intent.getStatField(),
                    intent.getLimit(), intent.getStartDate(), intent.getEndDate());
        } catch (Exception e) {
            log.error("의도 JSON 파싱 실패: {}", intentJson, e);
            throw new IllegalStateException("의도 파싱에 실패했습니다");
        }

        // 2단계: 의도 기반 데이터 조회
        int limit = intent.getLimit() != null ? intent.getLimit() : 5;
        Object data = fetchData(intent, limit);

        // 경기 쿼리 — 빈 결과는 LLM 없이 즉시 반환
        if ("SCHEDULE_QUERY".equals(intent.getIntentType())) {
            @SuppressWarnings("unchecked")
            List<Game> games = (List<Game>) data;
            if (games.isEmpty()) {
                String period = buildPeriodText(intent);
                String teamPart = intent.getTeam() != null ? intent.getTeam() + "의 " : "";
                return ChatResponseDto.builder()
                        .answer(period + " " + teamPart + "경기 데이터가 없습니다.")
                        .sources(List.of())
                        .intent(intent)
                        .build();
            }
        }

        // 3단계: 답변 생성
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("데이터 직렬화 실패", e);
        }

        String statHint = intent.getStatField() != null && !intent.getStatField().isBlank()
            ? "\n강조할 통계: " + intent.getStatField() : "";
        String intentSummary = String.format("""
            ## 조회 의도 요약 (이미 분석 완료)
            의도 유형: %s
            날짜 범위: %s ~ %s
            팀: %s
            선수: %s
            정렬: %s
            """,
            intent.getIntentType() != null ? intent.getIntentType() : "(없음)",
            intent.getStartDate()  != null ? intent.getStartDate()  : "(없음)",
            intent.getEndDate()    != null ? intent.getEndDate()    : "(없음)",
            intent.getTeam()       != null ? intent.getTeam()       : "(없음)",
            intent.getPlayerName() != null ? intent.getPlayerName() : "(없음)",
            intent.getSortBy()     != null ? intent.getSortBy()     : "(없음)"
        );
        String userMessage = intentSummary
            + "\n전처리된 질문(절대 날짜 + 정규화 팀명): " + normalizedInput
            + "\n원본 질문: " + question
            + statHint
            + "\n\n조회 데이터:\n" + dataJson;

        String answer = claudeApiClient.complete(
            timeContext + "\n" + answerGenerationPrompt, userMessage);
        log.debug("답변 생성 완료 - {}자", answer.length());

        @SuppressWarnings("unchecked")
        List<Player> players = "SCHEDULE_QUERY".equals(intent.getIntentType())
            ? List.of()
            : (List<Player>) data;

        return ChatResponseDto.builder()
                .answer(answer)
                .sources(players)
                .intent(intent)
                .build();
    }

    // LLM 전달 전 상대 날짜 표현을 절대 날짜(yyyy-MM-dd)로 치환
    private String preprocessQuestion(String question) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);
        LocalDate thisWeekMon = today.with(DayOfWeek.MONDAY);
        LocalDate thisWeekSat = today.with(DayOfWeek.SATURDAY);
        LocalDate thisWeekSun = today.with(DayOfWeek.SUNDAY);
        LocalDate lastWeekMon = thisWeekMon.minusWeeks(1);
        LocalDate lastWeekSat = thisWeekSat.minusWeeks(1);
        LocalDate lastWeekSun = thisWeekSun.minusWeeks(1);
        LocalDate nextWeekMon = thisWeekMon.plusWeeks(1);
        LocalDate nextWeekSun = thisWeekSun.plusWeeks(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());

        String r = question;

        // 복합 표현 먼저 (부분 치환 방지)
        r = r.replace("이번 주말", thisWeekSat + "부터 " + thisWeekSun);
        r = r.replace("이번주말",  thisWeekSat + "부터 " + thisWeekSun);
        r = r.replace("지난 주말", lastWeekSat + "부터 " + lastWeekSun);
        r = r.replace("지난주말",  lastWeekSat + "부터 " + lastWeekSun);
        r = r.replace("이번 주",   thisWeekMon + "부터 " + thisWeekSun);
        r = r.replace("이번주",    thisWeekMon + "부터 " + thisWeekSun);
        r = r.replace("지난 주",   lastWeekMon + "부터 " + lastWeekSun);
        r = r.replace("지난주",    lastWeekMon + "부터 " + lastWeekSun);
        r = r.replace("다음 주",   nextWeekMon + "부터 " + nextWeekSun);
        r = r.replace("다음주",    nextWeekMon + "부터 " + nextWeekSun);
        r = r.replace("이번 달",   monthStart  + "부터 " + monthEnd);
        r = r.replace("이번달",    monthStart  + "부터 " + monthEnd);

        // 단일 날짜
        r = r.replace("그저께", today.minusDays(2).toString());
        r = r.replace("모레",   today.plusDays(2).toString());
        r = r.replace("어제",   yesterday.toString());
        r = r.replace("오늘",   today.toString());
        r = r.replace("내일",   tomorrow.toString());

        // 범위 불명확 표현 — LLM에 맡기되 힌트 추가
        if (r.contains("다음 경기") || r.contains("다음경기")) {
            r += " (참고: " + today + "부터 14일 이내)";
        }
        if (r.contains("최근")) {
            r += " (참고: " + today.minusDays(7) + "부터 " + today + ")";
        }

        return r;
    }

    private Object fetchData(IntentDto intent, int limit) {
        if ("SCHEDULE_QUERY".equals(intent.getIntentType())) {
            return fetchGames(intent);
        }
        return fetchPlayers(intent, limit);
    }

    private List<Game> fetchGames(IntentDto intent) {
        LocalDate start = intent.getStartDate();
        LocalDate end   = intent.getEndDate();
        String team     = intent.getTeam();

        if (start == null) return List.of();
        if (end == null) end = start;

        if (team != null && !team.isBlank()) {
            return gameMapper.findByTeamAndDateRange(team, start, end);
        }
        return gameMapper.findByDateRange(start, end);
    }

    private String buildPeriodText(IntentDto intent) {
        LocalDate start = intent.getStartDate();
        LocalDate end   = intent.getEndDate();
        if (start == null) return "해당 기간";
        if (end == null || start.equals(end)) return start.toString();
        return start + " ~ " + end;
    }

    private String buildTimeContext() {
        LocalDate today = LocalDate.now();
        String dayOfWeekKo = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
        return String.format("""
            ## 시간 컨텍스트
            현재 날짜: %s (%s)
            오늘 요일: %s (이 정보는 절대적이므로 반드시 사용할 것)
            오늘: %s
            어제: %s
            내일: %s
            이번 주 월요일: %s
            이번 주 토요일: %s
            이번 주 일요일: %s
            지난 주 월요일: %s
            지난 주 토요일: %s
            지난 주 일요일: %s
            이번 달 1일: %s
            이번 달 말일: %s

            사용자가 상대 날짜 표현("오늘/어제/내일/이번 주/지난주/이번 달/지난달/이번 주말/지난 주말")을 사용하면 위 정보로 절대 날짜를 추론할 것. 절대 "날짜를 모른다"고 답하지 말 것.
            """,
            today, dayOfWeekKo,
            dayOfWeekKo,
            today,
            today.minusDays(1),
            today.plusDays(1),
            today.with(DayOfWeek.MONDAY),
            today.with(DayOfWeek.SATURDAY),
            today.with(DayOfWeek.SUNDAY),
            today.with(DayOfWeek.MONDAY).minusWeeks(1),
            today.with(DayOfWeek.SATURDAY).minusWeeks(1),
            today.with(DayOfWeek.SUNDAY).minusWeeks(1),
            today.withDayOfMonth(1),
            today.with(TemporalAdjusters.lastDayOfMonth())
        );
    }

    // 긴 별칭을 먼저 매칭하여 부분 치환 오류 방지
    private String normalizeTeamNames(String input) {
        String result = input;
        for (var entry : TEAM_ALIASES.entrySet().stream()
                .sorted((a, b) -> b.getKey().length() - a.getKey().length())
                .toList()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private List<Player> fetchPlayers(IntentDto intent, int limit) {
        if (intent.getPlayerName() != null && !intent.getPlayerName().isBlank()) {
            PlayerSearchDto searchDto = new PlayerSearchDto();
            searchDto.setName(intent.getPlayerName());
            searchDto.setSize(limit);
            return playerMapper.findAll(searchDto);
        }

        if (intent.getSortBy() != null) {
            return fetchRanking(intent.getSortBy(), limit, intent);
        }

        if (intent.getTeam() != null && !intent.getTeam().isBlank()) {
            PlayerSearchDto searchDto = new PlayerSearchDto();
            searchDto.setTeam(intent.getTeam());
            searchDto.setPlayerType(intent.getPlayerType());
            searchDto.setSize(limit);
            return playerMapper.findAll(searchDto);
        }

        PlayerSearchDto searchDto = new PlayerSearchDto();
        searchDto.setPlayerType(intent.getPlayerType());
        searchDto.setSize(limit);
        return playerMapper.findAll(searchDto);
    }

    private List<Player> fetchRanking(String sortBy, int limit, IntentDto intent) {
        return switch (sortBy) {
            case "homeRuns"    -> playerService.getHomeRunRanking(limit);
            case "battingAvg"  -> playerService.getBattingRanking(limit);
            case "rbi"         -> playerService.getRbiRanking(limit);
            case "stolenBases" -> playerService.getStolenBasesRanking(limit);
            case "era"         -> playerService.getEraRanking(limit);
            case "wins"        -> playerService.getWinsRanking(limit);
            case "saves"       -> playerService.getSavesRanking(limit);
            case "holds"       -> playerService.getHoldsRanking(limit);
            default -> {
                PlayerSearchDto searchDto = new PlayerSearchDto();
                searchDto.setPlayerType(intent.getPlayerType());
                searchDto.setSize(limit);
                yield playerMapper.findAll(searchDto);
            }
        };
    }

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
