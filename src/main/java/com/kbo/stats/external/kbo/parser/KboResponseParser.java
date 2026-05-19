package com.kbo.stats.external.kbo.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbo.stats.external.kbo.dto.KboBoxScoreResponse;
import com.kbo.stats.external.kbo.dto.KboScoreBoardResponse;
import com.kbo.stats.external.kbo.dto.parsed.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class KboResponseParser {

    private static final Pattern HOME_RUN_PATTERN =
            Pattern.compile("(\\S+?)(\\d+)호\\((\\d+)회(\\d+)점\\s*([^\\s)]+)\\)");

    private static final Pattern SIMPLE_EVENT_PATTERN =
            Pattern.compile("(\\S+)\\((\\d+)회\\)");

    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────
    // public API
    // ─────────────────────────────────────────────────────

    public ParsedBoxScore parseAll(KboScoreBoardResponse sb, KboBoxScoreResponse bs) {
        return ParsedBoxScore.builder()
                .meta(parseMeta(sb, bs))
                .inningScores(parseInningScores(sb))
                .awayBatters(parseBatters(bs, "AWAY"))
                .homeBatters(parseBatters(bs, "HOME"))
                .awayPitchers(parsePitchers(bs, "AWAY"))
                .homePitchers(parsePitchers(bs, "HOME"))
                .events(parseEvents(bs))
                .build();
    }

    public ParsedGameMeta parseMeta(KboScoreBoardResponse sb, KboBoxScoreResponse bs) {
        String finishingHit = null;
        String umpires = null;
        int[] awayRhe = {0, 0, 0};
        int[] homeRhe = {0, 0, 0};

        try {
            JsonNode etc = objectMapper.readTree(bs.getTableEtc());
            for (JsonNode rowNode : etc.path("rows")) {
                JsonNode row = rowNode.path("row");
                String label = getText(row.get(0));
                String value = KboTextCleaner.clean(getText(row.get(1)));
                switch (label) {
                    case "결승타" -> finishingHit = value;
                    case "심판"   -> umpires = value;
                }
            }
        } catch (Exception e) {
            log.warn("tableEtc 파싱 실패 (meta): {}", e.getMessage());
        }

        try {
            JsonNode t3 = objectMapper.readTree(sb.getTable3());
            JsonNode rows = t3.path("rows");
            if (rows.size() >= 2) {
                awayRhe = parseRhe(rows.get(0).path("row"));
                homeRhe = parseRhe(rows.get(1).path("row"));
            }
        } catch (Exception e) {
            log.warn("table3(R/H/E) 파싱 실패: {}", e.getMessage());
        }

        int crowdCount = parseCrowdCount(sb.getCrowdCn());

        return ParsedGameMeta.builder()
                .kboGameId(sb.getGId())
                .gameDate(sb.getGDt())
                .leId(sb.getLeId())
                .srId(sb.getSrId())
                .seasonId(sb.getSeasonId())
                .homeTeamId(sb.getHomeId())
                .homeTeamName(sb.getHomeNm())
                .awayTeamId(sb.getAwayId())
                .awayTeamName(sb.getAwayNm())
                .stadium(sb.getSNm())
                .crowdCount(crowdCount)
                .startTime(sb.getStartTm())
                .endTime(sb.getEndTm())
                .useTime(sb.getUseTm())
                .awayScore(sb.getTScoreCn())
                .homeScore(sb.getBScoreCn())
                .maxInning(sb.getMaxInning())
                .realMaxInning(bs.getRealMaxInning())
                .homeSeasonW(sb.getHWCn())
                .homeSeasonL(sb.getHLCn())
                .homeSeasonD(sb.getHDCn())
                .awaySeasonW(sb.getAWCn())
                .awaySeasonL(sb.getALCn())
                .awaySeasonD(sb.getADCn())
                .awayHits(awayRhe[0])
                .awayErrors(awayRhe[1])
                .awayWalks(awayRhe[2])
                .homeHits(homeRhe[0])
                .homeErrors(homeRhe[1])
                .homeWalks(homeRhe[2])
                .finishingHit(finishingHit)
                .umpires(umpires)
                .build();
    }

    public List<ParsedInningScore> parseInningScores(KboScoreBoardResponse sb) {
        List<ParsedInningScore> result = new ArrayList<>();
        try {
            JsonNode table2 = objectMapper.readTree(sb.getTable2());
            JsonNode rows = table2.path("rows");
            if (rows.size() < 2) return result;
            collectInnings(result, rows.get(0).path("row"), "AWAY");
            collectInnings(result, rows.get(1).path("row"), "HOME");
        } catch (Exception e) {
            log.warn("이닝 스코어 파싱 실패: {}", e.getMessage());
        }
        return result;
    }

    public List<ParsedBatterLine> parseBatters(KboBoxScoreResponse bs, String teamSide) {
        int idx = "AWAY".equals(teamSide) ? 0 : 1;
        List<ParsedBatterLine> result = new ArrayList<>();
        if (bs.getArrHitter() == null || bs.getArrHitter().size() <= idx) return result;

        try {
            KboBoxScoreResponse.KboHitterTable ht = bs.getArrHitter().get(idx);
            JsonNode rows1 = objectMapper.readTree(ht.getTable1()).path("rows");
            JsonNode rows2 = objectMapper.readTree(ht.getTable2()).path("rows");
            JsonNode rows3 = objectMapper.readTree(ht.getTable3()).path("rows");

            for (int i = 0; i < rows1.size(); i++) {
                JsonNode r1 = rows1.get(i).path("row");
                JsonNode r2 = (i < rows2.size()) ? rows2.get(i).path("row") : null;
                JsonNode r3 = (i < rows3.size()) ? rows3.get(i).path("row") : null;

                result.add(ParsedBatterLine.builder()
                        .teamSide(teamSide)
                        .battingOrder(parseIntSafe(getText(r1.get(0))))
                        .position(KboTextCleaner.clean(getText(r1.get(1))))
                        .playerName(KboTextCleaner.clean(getText(r1.get(2))))
                        .atBats(r3 != null ? parseIntSafe(getText(r3.get(0))) : 0)
                        .hits(r3 != null ? parseIntSafe(getText(r3.get(1))) : 0)
                        .rbi(r3 != null ? parseIntSafe(getText(r3.get(2))) : 0)
                        .walks(r3 != null ? parseIntSafe(getText(r3.get(3))) : 0)
                        .seasonAvg(r3 != null ? parseDecimalSafe(getText(r3.get(4))) : null)
                        .inningResults(collectInningResults(r2))
                        .build());
            }
        } catch (Exception e) {
            log.warn("{} 타자 파싱 실패: {}", teamSide, e.getMessage());
        }
        return result;
    }

    public List<ParsedPitcherLine> parsePitchers(KboBoxScoreResponse bs, String teamSide) {
        int idx = "AWAY".equals(teamSide) ? 0 : 1;
        List<ParsedPitcherLine> result = new ArrayList<>();
        if (bs.getArrPitcher() == null || bs.getArrPitcher().size() <= idx) return result;

        try {
            JsonNode rows = objectMapper.readTree(bs.getArrPitcher().get(idx).getTable()).path("rows");
            int order = 1;
            for (JsonNode rowNode : rows) {
                JsonNode r = rowNode.path("row");
                // headers: 선수명(0) 등판(1) 결과(2) 승(3) 패(4) 세(5) 이닝(6) 타자(7) 투구수(8) 타수(9) 피안타(10) 홈런(11) 4사구(12) 삼진(13) 실점(14) 자책(15) 평균자책점(16)
                result.add(ParsedPitcherLine.builder()
                        .teamSide(teamSide)
                        .pitchOrder(order++)
                        .playerName(KboTextCleaner.clean(getText(r.get(0))))
                        .appearanceLabel(KboTextCleaner.cleanOrNull(getText(r.get(1))))
                        .result(mapResult(KboTextCleaner.cleanOrNull(getText(r.get(2)))))
                        .seasonW(parseIntSafe(getText(r.get(3))))
                        .seasonL(parseIntSafe(getText(r.get(4))))
                        .seasonS(parseIntSafe(getText(r.get(5))))
                        .inningsPitchedOuts(InningOutsConverter.convert(getText(r.get(6))))
                        .battersFaced(parseIntSafe(getText(r.get(7))))
                        .pitches(parseIntSafe(getText(r.get(8))))
                        .atBatsAgainst(parseIntSafe(getText(r.get(9))))
                        .hitsAgainst(parseIntSafe(getText(r.get(10))))
                        .homeRunsAgainst(parseIntSafe(getText(r.get(11))))
                        .walksHbp(parseIntSafe(getText(r.get(12))))
                        .strikeouts(parseIntSafe(getText(r.get(13))))
                        .runsAllowed(parseIntSafe(getText(r.get(14))))
                        .earnedRuns(parseIntSafe(getText(r.get(15))))
                        .seasonEra(parseDecimalSafe(getText(r.get(16))))
                        .build());
            }
        } catch (Exception e) {
            log.warn("{} 투수 파싱 실패: {}", teamSide, e.getMessage());
        }
        return result;
    }

    public List<ParsedGameEvent> parseEvents(KboBoxScoreResponse bs) {
        List<ParsedGameEvent> result = new ArrayList<>();
        try {
            JsonNode etc = objectMapper.readTree(bs.getTableEtc());
            for (JsonNode rowNode : etc.path("rows")) {
                JsonNode row = rowNode.path("row");
                String label = getText(row.get(0));
                String value = KboTextCleaner.clean(getText(row.get(1)));
                if (value == null || value.isEmpty()) continue;

                String eventType = toEventType(label);
                if (eventType == null) continue;

                if ("HOME_RUN".equals(eventType)) {
                    result.addAll(parseHomeRuns(value));
                } else {
                    result.addAll(parseSimpleEvents(eventType, value));
                }
            }
        } catch (Exception e) {
            log.warn("이벤트 파싱 실패: {}", e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────
    // private helpers
    // ─────────────────────────────────────────────────────

    private void collectInnings(List<ParsedInningScore> out, JsonNode cells, String teamSide) {
        for (int i = 0; i < cells.size(); i++) {
            String text = KboTextCleaner.cleanOrNull(getText(cells.get(i)));
            Integer score = (text == null || "-".equals(text)) ? null : parseIntSafe(text);
            out.add(ParsedInningScore.builder()
                    .inning(i + 1)
                    .teamSide(teamSide)
                    .score(score)
                    .build());
        }
    }

    private List<String> collectInningResults(JsonNode cells) {
        if (cells == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (JsonNode cell : cells) {
            list.add(KboTextCleaner.cleanOrNull(getText(cell)));
        }
        return list;
    }

    private int[] parseRhe(JsonNode row) {
        // row[0]=득점, row[1]=안타, row[2]=실책, row[3]=4사구
        return new int[]{
                parseIntSafe(getText(row.get(1))),
                parseIntSafe(getText(row.get(2))),
                parseIntSafe(getText(row.get(3)))
        };
    }

    private List<ParsedGameEvent> parseHomeRuns(String text) {
        List<ParsedGameEvent> list = new ArrayList<>();
        Matcher m = HOME_RUN_PATTERN.matcher(text);
        while (m.find()) {
            list.add(ParsedGameEvent.builder()
                    .eventType("HOME_RUN")
                    .playerName(m.group(1))
                    .seasonCount(parseIntSafe(m.group(2)))
                    .inning(parseIntSafe(m.group(3)))
                    .runs(parseIntSafe(m.group(4)))
                    .opponentPitcher(m.group(5))
                    .rawText(m.group(0))
                    .build());
        }
        return list;
    }

    private List<ParsedGameEvent> parseSimpleEvents(String eventType, String text) {
        List<ParsedGameEvent> list = new ArrayList<>();
        Matcher m = SIMPLE_EVENT_PATTERN.matcher(text);
        while (m.find()) {
            list.add(ParsedGameEvent.builder()
                    .eventType(eventType)
                    .playerName(m.group(1).trim())
                    .inning(parseIntSafe(m.group(2)))
                    .rawText(m.group(0))
                    .build());
        }
        return list;
    }

    private String toEventType(String label) {
        return switch (label) {
            case "홈런"  -> "HOME_RUN";
            case "3루타" -> "TRIPLE";
            case "2루타" -> "DOUBLE";
            case "실책"  -> "ERROR";
            case "병살타" -> "DOUBLE_PLAY";
            case "폭투"  -> "WILD_PITCH";
            default -> null;
        };
    }

    private String mapResult(String korResult) {
        return switch (korResult == null ? "" : korResult) {
            case "승" -> "WIN";
            case "패" -> "LOSE";
            case "세" -> "SAVE";
            case "홀드" -> "HOLD";
            default -> "NONE";
        };
    }

    private String getText(JsonNode cell) {
        if (cell == null || cell.isMissingNode()) return "";
        return cell.path("Text").asText("");
    }

    private int parseIntSafe(String s) {
        if (s == null) return 0;
        String cleaned = KboTextCleaner.cleanOrNull(s);
        if (cleaned == null) return 0;
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal parseDecimalSafe(String s) {
        if (s == null) return null;
        String cleaned = KboTextCleaner.cleanOrNull(s);
        if (cleaned == null) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseCrowdCount(String crowdCn) {
        if (crowdCn == null) return 0;
        try {
            return Integer.parseInt(crowdCn.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
