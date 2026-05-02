package com.kbo.stats.service;

import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlingService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MS = 15_000;
    private static final int CURRENT_SEASON = 2026;

    // Statiz: opt=0 타자, opt=1 투수
    private static final String STATIZ_BASE =
            "https://statiz.co.kr/stat.php?opt=%d&sopt=0&re=0&ession=" + CURRENT_SEASON;
    private static final String STATIZ_BATTER_URL  = String.format(STATIZ_BASE, 0);
    private static final String STATIZ_PITCHER_URL = String.format(STATIZ_BASE, 1);

    // KBO 공식 사이트 fallback
    private static final String KBO_BATTER_URL  =
            "https://www.koreabaseball.com/Record/Player/HitterBasic/Basic1.aspx";
    private static final String KBO_PITCHER_URL =
            "https://www.koreabaseball.com/Record/Player/PitcherBasic/Basic1.aspx";

    private final PlayerService playerService;

    public void crawlAll() {
        log.info("=== KBO 크롤링 시작 (시즌: {}) ===", CURRENT_SEASON);
        int batterCount  = crawlBatters();
        int pitcherCount = crawlPitchers();
        log.info("=== KBO 크롤링 완료 | 타자: {}명, 투수: {}명 ===", batterCount, pitcherCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 공개 진입점: Statiz 실패 시 KBO fallback
    // ─────────────────────────────────────────────────────────────────────────

    private int crawlBatters() {
        log.info("[타자] Statiz 크롤링 시도: {}", STATIZ_BATTER_URL);
        List<Player> result = crawlStatizBatters();
        if (!result.isEmpty()) {
            log.info("[타자] Statiz 성공: {}명 저장", result.size());
            return result.size();
        }
        log.warn("[타자] Statiz 결과 없음 → KBO 공식 사이트 fallback: {}", KBO_BATTER_URL);
        result = crawlKboBatters();
        log.info("[타자] KBO 결과: {}명 저장", result.size());
        return result.size();
    }

    private int crawlPitchers() {
        log.info("[투수] Statiz 크롤링 시도: {}", STATIZ_PITCHER_URL);
        List<Player> result = crawlStatizPitchers();
        if (!result.isEmpty()) {
            log.info("[투수] Statiz 성공: {}명 저장", result.size());
            return result.size();
        }
        log.warn("[투수] Statiz 결과 없음 → KBO 공식 사이트 fallback: {}", KBO_PITCHER_URL);
        result = crawlKboPitchers();
        log.info("[투수] KBO 결과: {}명 저장", result.size());
        return result.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Statiz 크롤링
    // ─────────────────────────────────────────────────────────────────────────

    private List<Player> crawlStatizBatters() {
        List<Player> players = new ArrayList<>();
        try {
            Document doc = fetchDocument(STATIZ_BATTER_URL, "https://statiz.co.kr");
            Elements rows = selectTableRows(doc);
            log.debug("[타자/Statiz] 파싱 대상 행: {}개", rows.size());
            for (Element row : rows) {
                try {
                    Player p = parseStatizBatterRow(row);
                    if (p != null) { players.add(p); playerService.saveOrUpdate(p); }
                } catch (Exception e) {
                    log.warn("[타자/Statiz] 행 파싱 실패: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[타자/Statiz] 연결 실패: {}", e.getMessage());
        }
        return players;
    }

    private List<Player> crawlStatizPitchers() {
        List<Player> players = new ArrayList<>();
        try {
            Document doc = fetchDocument(STATIZ_PITCHER_URL, "https://statiz.co.kr");
            Elements rows = selectTableRows(doc);
            log.debug("[투수/Statiz] 파싱 대상 행: {}개", rows.size());
            for (Element row : rows) {
                try {
                    Player p = parseStatizPitcherRow(row);
                    if (p != null) { players.add(p); playerService.saveOrUpdate(p); }
                } catch (Exception e) {
                    log.warn("[투수/Statiz] 행 파싱 실패: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[투수/Statiz] 연결 실패: {}", e.getMessage());
        }
        return players;
    }

    /**
     * Statiz 타자 기록 컬럼 (opt=0):
     * 0:순위 1:선수 2:팀 3:G 4:PA 5:AB 6:R 7:H 8:2B 9:3B 10:HR 11:TB
     * 12:RBI 13:SB 14:CS 15:BB 16:SO 17:GDP 18:BABIP 19:OPS 20:AVG 21:OBP 22:SLG
     */
    private Player parseStatizBatterRow(Element row) {
        Elements cols = row.select("td");
        if (cols.size() < 21) return null;

        String name = extractPlayerName(cols.get(1));
        String team = cols.get(2).text().trim();
        if (name.isBlank() || team.isBlank()) return null;
        if (!isNumeric(cols.get(3).text())) return null; // 헤더/소계 행 스킵

        return Player.builder()
                .name(name)
                .team(team)
                .position("타자")
                .playerType(PlayerType.BATTER)
                .games(parseInt(cols.get(3).text()))
                .hits(parseInt(cols.get(7).text()))
                .homeRuns(parseInt(cols.get(10).text()))
                .rbi(parseInt(cols.get(12).text()))
                .battingAvg(parseDouble(cols.get(20).text()))
                .build();
    }

    /**
     * Statiz 투수 기록 컬럼 (opt=1):
     * 0:순위 1:선수 2:팀 3:G 4:GS 5:CG 6:ShO 7:W 8:L 9:SV 10:HLD
     * 11:IP 12:BF 13:NP 14:H 15:HR 16:BB 17:HBP 18:SO 19:R 20:ER 21:ERA 22:WHIP
     */
    private Player parseStatizPitcherRow(Element row) {
        Elements cols = row.select("td");
        if (cols.size() < 22) return null;

        String name = extractPlayerName(cols.get(1));
        String team = cols.get(2).text().trim();
        if (name.isBlank() || team.isBlank()) return null;
        if (!isNumeric(cols.get(3).text())) return null;

        return Player.builder()
                .name(name)
                .team(team)
                .position("투수")
                .playerType(PlayerType.PITCHER)
                .games(parseInt(cols.get(3).text()))
                .wins(parseInt(cols.get(7).text()))
                .era(parseDouble(cols.get(21).text()))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KBO 공식 사이트 Fallback 크롤링
    // ─────────────────────────────────────────────────────────────────────────

    private List<Player> crawlKboBatters() {
        List<Player> players = new ArrayList<>();
        try {
            Document doc = fetchDocument(KBO_BATTER_URL, "https://www.koreabaseball.com");
            Elements rows = selectKboTableRows(doc);
            log.debug("[타자/KBO] 파싱 대상 행: {}개", rows.size());
            for (Element row : rows) {
                try {
                    Player p = parseKboBatterRow(row);
                    if (p != null) { players.add(p); playerService.saveOrUpdate(p); }
                } catch (Exception e) {
                    log.warn("[타자/KBO] 행 파싱 실패: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[타자/KBO] 연결 실패: {}", e.getMessage());
        }
        return players;
    }

    private List<Player> crawlKboPitchers() {
        List<Player> players = new ArrayList<>();
        try {
            Document doc = fetchDocument(KBO_PITCHER_URL, "https://www.koreabaseball.com");
            Elements rows = selectKboTableRows(doc);
            log.debug("[투수/KBO] 파싱 대상 행: {}개", rows.size());
            for (Element row : rows) {
                try {
                    Player p = parseKboPitcherRow(row);
                    if (p != null) { players.add(p); playerService.saveOrUpdate(p); }
                } catch (Exception e) {
                    log.warn("[투수/KBO] 행 파싱 실패: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[투수/KBO] 연결 실패: {}", e.getMessage());
        }
        return players;
    }

    /**
     * KBO 공식 타자 기록 컬럼:
     * 0:순위 1:선수명 2:팀 3:AVG 4:G 5:PA 6:AB 7:R 8:H 9:2B 10:3B 11:HR 12:TB 13:RBI ...
     */
    private Player parseKboBatterRow(Element row) {
        Elements cols = row.select("td");
        if (cols.size() < 14) return null;

        String name = extractPlayerName(cols.get(1));
        String team = cols.get(2).text().trim();
        if (name.isBlank() || team.isBlank()) return null;
        if (!isNumeric(cols.get(4).text())) return null;

        return Player.builder()
                .name(name)
                .team(team)
                .position("타자")
                .playerType(PlayerType.BATTER)
                .battingAvg(parseDouble(cols.get(3).text()))
                .games(parseInt(cols.get(4).text()))
                .hits(parseInt(cols.get(8).text()))
                .homeRuns(parseInt(cols.get(11).text()))
                .rbi(parseInt(cols.get(13).text()))
                .build();
    }

    /**
     * KBO 공식 투수 기록 컬럼:
     * 0:순위 1:선수명 2:팀 3:ERA 4:G 5:W 6:L 7:SV 8:HLD 9:IP ...
     */
    private Player parseKboPitcherRow(Element row) {
        Elements cols = row.select("td");
        if (cols.size() < 6) return null;

        String name = extractPlayerName(cols.get(1));
        String team = cols.get(2).text().trim();
        if (name.isBlank() || team.isBlank()) return null;
        if (!isNumeric(cols.get(4).text())) return null;

        return Player.builder()
                .name(name)
                .team(team)
                .position("투수")
                .playerType(PlayerType.PITCHER)
                .era(parseDouble(cols.get(3).text()))
                .games(parseInt(cols.get(4).text()))
                .wins(parseInt(cols.get(5).text()))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 공통 유틸리티
    // ─────────────────────────────────────────────────────────────────────────

    private Document fetchDocument(String url, String referrer) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(referrer)
                .timeout(TIMEOUT_MS)
                .get();
    }

    /** Statiz 테이블 행 선택: id > class > 첫 번째 table 순으로 시도 */
    private Elements selectTableRows(Document doc) {
        Elements rows = doc.select("table#mytable tbody tr");
        if (rows.isEmpty()) rows = doc.select("table.tablesorter tbody tr");
        if (rows.isEmpty()) rows = doc.select("div.container table tbody tr");
        if (rows.isEmpty()) rows = doc.select("table tbody tr");
        return rows;
    }

    /** KBO 공식 사이트 테이블 행 선택 */
    private Elements selectKboTableRows(Document doc) {
        Elements rows = doc.select("table#tblRecord tbody tr");
        if (rows.isEmpty()) rows = doc.select(".tData01 tbody tr");
        if (rows.isEmpty()) rows = doc.select("table tbody tr");
        return rows;
    }

    /** 선수명 추출: <a> 링크 텍스트 우선, 없으면 셀 텍스트 */
    private String extractPlayerName(Element col) {
        Element link = col.selectFirst("a");
        return (link != null ? link.text() : col.text()).trim();
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            Double.parseDouble(s.trim().replace(",", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
