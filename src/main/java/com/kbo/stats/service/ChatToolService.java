package com.kbo.stats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import com.kbo.stats.dto.ChatResponseDto;
import com.kbo.stats.dto.MatchupSummaryDto;
import com.kbo.stats.dto.PlayerMatchupListDto;
import com.kbo.stats.dto.PlayerSearchDto;
import com.kbo.stats.dto.PlayerVsTeamDto;
import com.kbo.stats.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatToolService {

    private final ClaudeApiClient claudeApiClient;
    private final PlayerMapper playerMapper;
    private final PlayerVsTeamService playerVsTeamService;
    private final ObjectMapper objectMapper;
    private final GameService gameService;
    private final DashboardService dashboardService;
    private final GameDetailService gameDetailService;
    private final PlayerService playerService;
    private final MatchupService matchupService;

    private static final String SYSTEM_PROMPT = "당신은 KBO 리그 통계 전문 어시스턴트입니다. " +
            "현재는 2026년 KBO 시즌입니다. 연도가 명시되지 않은 날짜는 모두 2026년으로 가정하세요. " + // ← 추가
            "사용자가 선수 통계, 상대 팀 전적 등을 물어보면 제공된 도구를 사용해 데이터를 조회한 뒤 자연스러운 한국어로 답변하세요. " +
            "데이터를 모르거나 도구가 없는 영역의 질문은 정직하게 모른다고 답하세요. " +
            "수치는 정확히 인용하고, 불필요한 부연 설명은 피하세요. " +
            "날짜는 YYYY-MM-DD 형식으로 받고, 팀 이름은 한글(KIA, KT, LG, NC, SSG, 두산, 롯데, 삼성, 키움, 한화) 그대로 사용하세요. " +
            "타자-투수 1:1 매치업 데이터도 조회 가능합니다. 박스스코어 데이터에서 추론한 결과로, 박스스코어가 없는 경기는 매치업이 없을 수 있습니다.";

    private static final int MAX_TURNS = 5;

    private static final List<Map<String, Object>> TOOLS = List.of(
            Map.of(
                    "name", "getPlayerSeasonStats",
                    "description", "특정 선수의 시즌 통계(타율, 홈런, 타점, 출루율, ERA, 승, 세이브 등)를 조회합니다. 선수 이름이 필요합니다.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "playerName", Map.of(
                                            "type", "string",
                                            "description", "선수 이름 (예: 오스틴, 류현진, 김도영)")),
                            "required", List.of("playerName"))),
            Map.of(
                    "name", "getPlayerVsTeamStats",
                    "description", "특정 선수가 상대 팀(들)에게 기록한 전적을 조회합니다. opponent를 지정하지 않으면 모든 상대 팀의 전적을 반환합니다.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "playerName", Map.of("type", "string", "description", "선수 이름"),
                                    "opponent",
                                    Map.of("type", "string", "description", "상대 팀 (예: SSG, LG, 두산). 없으면 모든 팀")),
                            "required", List.of("playerName"))),
            Map.of(
                    "name", "getPitcherVsTeamStats",
                    "description", "특정 투수가 상대 팀(들)에게 등판한 기록(이닝, 자책점, ERA, WHIP)을 조회합니다. opponent 없으면 모든 상대 팀.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "playerName", Map.of("type", "string", "description", "투수 이름 (예: 류현진, 고영표)"),
                                    "opponent", Map.of("type", "string", "description", "상대 팀 (예: KIA, 두산). 없으면 모든 팀")),
                            "required", List.of("playerName"))),
            Map.of(
                    "name", "getGameDetail",
                    "description", "특정 경기의 박스스코어(이닝별 점수, 타자/투수 출장 기록)를 조회합니다.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "gameDate", Map.of("type", "string", "description", "경기 날짜 (YYYY-MM-DD)"),
                                    "awayTeam", Map.of("type", "string", "description", "원정 팀 이름"),
                                    "homeTeam", Map.of("type", "string", "description", "홈 팀 이름")),
                            "required", List.of("gameDate", "awayTeam", "homeTeam"))),
            Map.of(
                    "name", "searchGames",
                    "description", "경기 일정 또는 결과를 검색합니다. 날짜 또는 팀으로 필터링. 둘 다 없으면 오늘 경기.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "gameDate", Map.of("type", "string", "description", "경기 날짜 (YYYY-MM-DD). 없으면 오늘"),
                                    "team", Map.of("type", "string", "description", "팀 이름. 없으면 전체 팀")),
                            "required", List.of())),
            Map.of(
                    "name", "getTeamStanding",
                    "description", "현재 팀 순위(승률순)를 조회합니다. 입력 없음.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(),
                            "required", List.of())),
            Map.of(
                    "name", "getStatRanking",
                    "description",
                    "부문별 선수 랭킹을 조회합니다. statName 값: battingAvg, homeRuns, rbi, stolenBases, era, wins, saves, holds 중 하나.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "statName", Map.of("type", "string",
                                            "description",
                                            "통계 부문: battingAvg, homeRuns, rbi, stolenBases, era, wins, saves, holds"),
                                    "limit", Map.of("type", "integer", "description", "조회 인원 수 (기본 5, 최대 20)")),
                            "required", List.of("statName"))),
            Map.of(
                    "name", "getMatchup",
                    "description", "특정 타자 vs 특정 투수의 1:1 매치업 기록을 조회합니다. 타석 수, 안타, 홈런, 볼넷, 삼진, 타율 등을 포함합니다.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "batter", Map.of("type", "string", "description", "타자 이름"),
                                    "pitcher", Map.of("type", "string", "description", "투수 이름")),
                            "required", List.of("batter", "pitcher"))),
            Map.of(
                    "name", "getPlayerMatchups",
                    "description", "특정 선수가 시즌 동안 만난 모든 상대 선수의 매치업 요약을 조회합니다. 타자면 모든 상대 투수, 투수면 모든 상대 타자.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "playerName", Map.of("type", "string", "description", "선수 이름"),
                                    "playerType", Map.of("type", "string",
                                            "description", "'BATTER' or 'PITCHER'. 미지정 시 자동 판별.")),
                            "required", List.of("playerName"))));

    @Cacheable(value = "chatAnswers", key = "'v2:' + #question.toLowerCase().trim()")
    public ChatResponseDto answer(String question) {
        log.info("Tool Use 챗봇 호출: {}", question);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", question));

        String finalText = null;
        int turn = 0;

        while (turn < MAX_TURNS) {
            turn++;
            log.debug("=== Turn {} 시작 ===", turn);

            JsonNode response = claudeApiClient.completeWithTools(SYSTEM_PROMPT, messages, TOOLS);
            String stopReason = response.path("stop_reason").asText();
            JsonNode contentArray = response.path("content");

            log.debug("응답 stop_reason: {}, content blocks: {}", stopReason, contentArray.size());

            List<Map<String, Object>> assistantContent = new ArrayList<>();
            for (JsonNode block : contentArray) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    assistantContent.add(Map.of(
                            "type", "text",
                            "text", block.path("text").asText()));
                } else if ("tool_use".equals(type)) {
                    assistantContent.add(Map.of(
                            "type", "tool_use",
                            "id", block.path("id").asText(),
                            "name", block.path("name").asText(),
                            "input", objectMapper.convertValue(block.path("input"), Map.class)));
                }
            }
            messages.add(Map.of("role", "assistant", "content", assistantContent));

            if ("end_turn".equals(stopReason)) {
                for (JsonNode block : contentArray) {
                    if ("text".equals(block.path("type").asText())) {
                        finalText = block.path("text").asText();
                    }
                }
                break;
            }

            if ("tool_use".equals(stopReason)) {
                List<Map<String, Object>> toolResults = new ArrayList<>();
                for (JsonNode block : contentArray) {
                    if (!"tool_use".equals(block.path("type").asText()))
                        continue;

                    String toolId = block.path("id").asText();
                    String toolName = block.path("name").asText();
                    JsonNode input = block.path("input");

                    log.info("Tool 호출: {} input={}", toolName, input);
                    String result = executeTool(toolName, input);
                    log.debug("Tool 결과 ({}자): {}", result.length(),
                            result.length() > 200 ? result.substring(0, 200) + "..." : result);

                    toolResults.add(Map.of(
                            "type", "tool_result",
                            "tool_use_id", toolId,
                            "content", result));
                }
                messages.add(Map.of("role", "user", "content", toolResults));
                continue;
            }

            log.warn("예상 못 한 stop_reason: {}", stopReason);
            break;
        }

        if (finalText == null) {
            finalText = "죄송합니다, 답변을 생성하지 못했습니다.";
        }

        return ChatResponseDto.builder()
                .answer(finalText)
                .sources(Collections.emptyList())
                .build();
    }

    private String executeTool(String toolName, JsonNode input) {
        try {
            return switch (toolName) {
                case "getPlayerSeasonStats" -> {
                    String playerName = input.path("playerName").asText();
                    yield getPlayerSeasonStats(playerName);
                }
                case "getPlayerVsTeamStats" -> {
                    String playerName = input.path("playerName").asText();
                    String opponent = input.has("opponent") && !input.path("opponent").isNull()
                            ? input.path("opponent").asText()
                            : null;
                    yield getPlayerVsTeamStats(playerName, opponent);
                }
                case "getPitcherVsTeamStats" -> {
                    String playerName = input.path("playerName").asText();
                    String opponent = input.has("opponent") && !input.path("opponent").isNull()
                            ? input.path("opponent").asText()
                            : null;
                    yield getPitcherVsTeamStats(playerName, opponent);
                }
                case "getGameDetail" -> {
                    String gameDate = input.path("gameDate").asText();
                    String awayTeam = input.path("awayTeam").asText();
                    String homeTeam = input.path("homeTeam").asText();
                    yield getGameDetail(gameDate, awayTeam, homeTeam);
                }
                case "searchGames" -> {
                    String gameDate = input.has("gameDate") && !input.path("gameDate").isNull()
                            && !input.path("gameDate").asText().isBlank()
                                    ? input.path("gameDate").asText()
                                    : null;
                    String team = input.has("team") && !input.path("team").isNull()
                            && !input.path("team").asText().isBlank()
                                    ? input.path("team").asText()
                                    : null;
                    yield searchGames(gameDate, team);
                }
                case "getTeamStanding" -> getTeamStanding();
                case "getStatRanking" -> {
                    String statName = input.path("statName").asText();
                    Integer limit = input.has("limit") && !input.path("limit").isNull()
                            ? input.path("limit").asInt()
                            : null;
                    yield getStatRanking(statName, limit);
                }
                case "getMatchup" -> {
                    String batter = input.path("batter").asText();
                    String pitcher = input.path("pitcher").asText();
                    MatchupSummaryDto matchupResult = matchupService.getMatchup(batter, pitcher);
                    if (matchupResult.getPlateAppearances() == 0) {
                        yield "{\"error\":\"" + batter + " vs " + pitcher + " 매치업 기록 없음\"}";
                    }
                    Map<String, Object> trimmed = new LinkedHashMap<>();
                    trimmed.put("batter", matchupResult.getBatterName());
                    trimmed.put("pitcher", matchupResult.getPitcherName());
                    trimmed.put("plateAppearances", matchupResult.getPlateAppearances());
                    trimmed.put("atBats", matchupResult.getAtBats());
                    trimmed.put("hits", matchupResult.getHits());
                    trimmed.put("homeRuns", matchupResult.getHomeRuns());
                    trimmed.put("walks", matchupResult.getWalks());
                    trimmed.put("strikeouts", matchupResult.getStrikeouts());
                    trimmed.put("avg", matchupResult.getAvg());
                    trimmed.put("obp", matchupResult.getObp());
                    trimmed.put("records", matchupResult.getRecords().stream()
                            .limit(10)
                            .map(r -> Map.of(
                                    "gameDate", r.getGameDate().toString(),
                                    "inning", r.getInning(),
                                    "result", r.getResult(),
                                    "category", r.getResultCategory()))
                            .toList());
                    yield objectMapper.writeValueAsString(trimmed);
                }
                case "getPlayerMatchups" -> {
                    String name = input.path("playerName").asText();
                    String type = input.has("playerType") && !input.path("playerType").isNull()
                            && !input.path("playerType").asText().isBlank()
                                    ? input.path("playerType").asText() : null;
                    if (type == null) {
                        PlayerSearchDto s = new PlayerSearchDto();
                        s.setName(name);
                        s.setSize(1);
                        List<Player> found = playerMapper.findAll(s);
                        if (found.isEmpty()) {
                            yield "{\"error\":\"" + name + " 선수를 찾을 수 없습니다.\"}";
                        }
                        type = found.get(0).getPlayerType().name();
                    }
                    PlayerMatchupListDto listResult = "BATTER".equals(type)
                            ? matchupService.getBatterMatchups(name)
                            : matchupService.getPitcherMatchups(name);
                    if (listResult.getMatchups().isEmpty()) {
                        yield "{\"error\":\"" + name + " 매치업 기록 없음\"}";
                    }
                    Map<String, Object> trimmed = new LinkedHashMap<>();
                    trimmed.put("playerName", listResult.getPlayerName());
                    trimmed.put("playerType", listResult.getPlayerType());
                    trimmed.put("totalOpponents", listResult.getMatchups().size());
                    trimmed.put("topMatchups", listResult.getMatchups().stream()
                            .limit(15)
                            .map(m -> Map.of(
                                    "batter", m.getBatterName(),
                                    "pitcher", m.getPitcherName(),
                                    "plateAppearances", m.getPlateAppearances(),
                                    "atBats", m.getAtBats(),
                                    "hits", m.getHits(),
                                    "homeRuns", m.getHomeRuns(),
                                    "walks", m.getWalks(),
                                    "strikeouts", m.getStrikeouts(),
                                    "avg", m.getAvg()))
                            .toList());
                    yield objectMapper.writeValueAsString(trimmed);
                }
                default -> "{\"error\":\"알 수 없는 도구: " + toolName + "\"}";
            };
        } catch (Exception e) {
            log.error("Tool 실행 실패: {}", toolName, e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String getPlayerSeasonStats(String playerName) throws Exception {
        PlayerSearchDto searchDto = new PlayerSearchDto();
        searchDto.setName(playerName);
        searchDto.setSize(1);
        List<Player> found = playerMapper.findAll(searchDto);

        if (found.isEmpty()) {
            return "{\"error\":\"" + playerName + " 선수를 찾을 수 없습니다.\"}";
        }

        return objectMapper.writeValueAsString(found.get(0));
    }

    private String getPlayerVsTeamStats(String playerName, String opponent) throws Exception {
        PlayerSearchDto searchDto = new PlayerSearchDto();
        searchDto.setName(playerName);
        searchDto.setSize(1);
        List<Player> found = playerMapper.findAll(searchDto);

        if (found.isEmpty()) {
            return "{\"error\":\"" + playerName + " 선수를 찾을 수 없습니다.\"}";
        }

        Player player = found.get(0);
        List<PlayerVsTeamDto> vsTeams = playerVsTeamService.findByPlayerId(player.getId());

        if (opponent != null && !opponent.isBlank()) {
            vsTeams = vsTeams.stream()
                    .filter(v -> v.getOpponent().equalsIgnoreCase(opponent)
                            || v.getOpponent().contains(opponent))
                    .toList();
        }

        if (vsTeams.isEmpty()) {
            return "{\"error\":\"" + playerName +
                    (opponent != null ? " vs " + opponent : "") +
                    " 박스스코어 데이터가 없습니다.\"}";
        }

        return objectMapper.writeValueAsString(Map.of(
                "playerName", playerName,
                "playerType", player.getPlayerType().name(),
                "vsTeams", vsTeams));
    }

    private String getPitcherVsTeamStats(String playerName, String opponent) throws Exception {
        PlayerSearchDto searchDto = new PlayerSearchDto();
        searchDto.setName(playerName);
        searchDto.setSize(1);
        List<Player> found = playerMapper.findAll(searchDto);

        if (found.isEmpty()) {
            return "{\"error\":\"" + playerName + " 선수를 찾을 수 없습니다.\"}";
        }

        Player player = found.get(0);
        if (player.getPlayerType() != PlayerType.PITCHER) {
            return "{\"error\":\"" + playerName + " 선수는 투수가 아닙니다.\"}";
        }

        List<PlayerVsTeamDto> vsTeams = playerVsTeamService.findByPlayerId(player.getId());

        if (opponent != null && !opponent.isBlank()) {
            vsTeams = vsTeams.stream()
                    .filter(v -> v.getOpponent().equalsIgnoreCase(opponent)
                            || v.getOpponent().contains(opponent))
                    .toList();
        }

        if (vsTeams.isEmpty()) {
            return "{\"error\":\"" + playerName +
                    (opponent != null ? " vs " + opponent : "") +
                    " 투수 기록 데이터가 없습니다.\"}";
        }

        return objectMapper.writeValueAsString(Map.of(
                "playerName", playerName,
                "playerType", "PITCHER",
                "vsTeams", vsTeams));
    }

    private String getGameDetail(String gameDate, String awayTeam, String homeTeam) throws Exception {
        LocalDate date = LocalDate.parse(gameDate);
        List<Game> games = gameService.getGamesByDate(date);
        Game target = games.stream()
                .filter(g -> (g.getAwayTeam().equalsIgnoreCase(awayTeam) && g.getHomeTeam().equalsIgnoreCase(homeTeam))
                        || (g.getAwayTeam().equalsIgnoreCase(homeTeam) && g.getHomeTeam().equalsIgnoreCase(awayTeam)))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return "{\"error\":\"" + gameDate + " " + awayTeam + " vs " + homeTeam + " 경기를 찾을 수 없습니다.\"}";
        }

        return objectMapper.writeValueAsString(gameDetailService.findGameDetail(target.getId()));
    }

    private String searchGames(String gameDate, String team) throws Exception {
        List<Game> games;
        if (gameDate != null && team != null) {
            LocalDate date = LocalDate.parse(gameDate);
            games = gameService.getGamesByTeam(team, date, date);
        } else if (gameDate != null) {
            games = gameService.getGamesByDate(LocalDate.parse(gameDate));
        } else if (team != null) {
            games = gameService.getRecentByTeam(team, 10);
        } else {
            games = gameService.getGamesByDate(LocalDate.now());
        }

        List<Map<String, Object>> result = games.stream()
                .limit(20)
                .map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", g.getGameDate().toString());
                    m.put("awayTeam", g.getAwayTeam());
                    m.put("homeTeam", g.getHomeTeam());
                    m.put("awayScore", g.getAwayScore());
                    m.put("homeScore", g.getHomeScore());
                    m.put("status", g.getStatus() != null ? g.getStatus().name() : null);
                    m.put("stadium", g.getStadium());
                    return m;
                })
                .collect(Collectors.toList());

        return objectMapper.writeValueAsString(result);
    }

    private String getTeamStanding() throws Exception {
        return objectMapper.writeValueAsString(dashboardService.getTeamStandings());
    }

    private String getStatRanking(String statName, Integer limit) throws Exception {
        int n = Math.min(limit != null ? limit : 5, 20);
        List<Player> ranking = switch (statName) {
            case "battingAvg" -> playerService.getBattingRanking(n);
            case "homeRuns" -> playerService.getHomeRunRanking(n);
            case "rbi" -> playerService.getRbiRanking(n);
            case "stolenBases" -> playerService.getStolenBasesRanking(n);
            case "era" -> playerService.getEraRanking(n);
            case "wins" -> playerService.getWinsRanking(n);
            case "saves" -> playerService.getSavesRanking(n);
            case "holds" -> playerService.getHoldsRanking(n);
            default -> null;
        };

        if (ranking == null) {
            return "{\"error\":\"알 수 없는 통계 부문: " + statName + "\"}";
        }

        return objectMapper.writeValueAsString(ranking);
    }
}
