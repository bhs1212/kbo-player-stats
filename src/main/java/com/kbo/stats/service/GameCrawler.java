package com.kbo.stats.service;

import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.GameStatus;
import com.kbo.stats.mapper.GameMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameCrawler {

    private final GameMapper gameMapper;

    private static final String KBO_SCHEDULE_BASE =
            "https://www.koreabaseball.com/Schedule/Schedule.aspx";
    private static final String NAVER_SCHEDULE_URL =
            "https://sports.news.naver.com/kbaseball/schedule/index";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36";
    private static final long WAIT_SECONDS   = 10;
    private static final int  MIN_SOURCE_LEN = 50_000;

    // KBO 시즌 범위 (3월~11월)
    private static final int SEASON_START_MONTH = 3;
    private static final int SEASON_END_MONTH   = 11;

    // Naver SPA CSS Module 선택자
    private static final String[] CONTAINER_SELECTORS = {
        "[class*=ScheduleAllType_match_list_group__]",
        "[class*=ScheduleAllType_match_list__]",
        "[class*=schedule_match_list]",
        "[class*=match_list__]",
    };
    private static final String[] DATE_ITEM_SELECTORS = {
        "[class*=ScheduleAllType_match_list_group__]",
        "[class*=match_list_group]",
        "[class*=date_item__]",
    };
    private static final String[] GAME_ITEM_SELECTORS = {
        "[class*=MatchBox_match_item__]",
        "[class*=ScheduleLeagueType_match_item__]",
        "[class*=ScheduleLeagueType_match__]",
        "[class*=match_item__]",
        "[class*=game_item__]",
    };
    private static final String[] TEAM_NAME_SELECTORS = {
        "[class*=team_name__]",
        "[class*=team__] strong",
        "strong[class*=team]",
        "em[class*=team]",
    };
    private static final String[] SCORE_SELECTORS = {
        "[class*=score__]",
        "em[class*=score]",
        "span[class*=score]",
    };

    // ── 스케줄러 ──────────────────────────────────────────────────

    @Scheduled(cron = "0 0 4 * * *")
    public void scheduledMorning() {
        log.info("[경기 크롤러] 새벽 4시 스케줄 실행");
        crawlCurrentMonth();
    }

    @Scheduled(cron = "0 0 23 * * *")
    public void scheduledEvening() {
        log.info("[경기 크롤러] 오후 11시 스케줄 실행");
        crawlCurrentMonth();
    }

    // ── 공개 API ──────────────────────────────────────────────────

    public int crawlCurrentMonth() {
        LocalDate now = LocalDate.now();
        String seriesIds = resolveSeriesIds(now.getYear(), now.getMonthValue());
        return crawlMonth(now.getYear(), now.getMonthValue(), seriesIds);
    }

    /** 현재 월 + 다음 월 경기 일정 갱신 (효율적 갱신) */
    public int crawlActiveMonths() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int currentMonth = now.getMonthValue();
        int nextMonth = currentMonth + 1;

        log.info("[경기 크롤러] 활성 월 갱신 시작 ({}년 {}월~{}월)", year, currentMonth, nextMonth);
        int total = 0;

        String currentSeries = resolveSeriesIds(year, currentMonth);
        total += crawlMonth(year, currentMonth, currentSeries);

        if (nextMonth <= SEASON_END_MONTH) {
            String nextSeries = resolveSeriesIds(year, nextMonth);
            total += crawlMonth(year, nextMonth, nextSeries);
        } else {
            log.info("[경기 크롤러] 다음 월이 시즌 범위 밖 - 건너뜀");
        }

        log.info("[경기 크롤러] 활성 월 갱신 완료 - 합계 {}건", total);
        return total;
    }

    /** 현재 연도 시즌 전체(3~11월) 크롤링 */
    public int crawlAllGames() {
        return crawlAllGames(LocalDate.now().getYear());
    }

    /** 지정 연도 시즌 전체(3~11월) 크롤링 - 단일 드라이버로 드롭다운 순회, seriesId 변경 시만 재시작 */
    public int crawlAllGames(int year) {
        log.info("[경기 크롤러] ===== {}년 시즌 전체 크롤링 시작 ({}월~{}월) =====",
                 year, SEASON_START_MONTH, SEASON_END_MONTH);
        int[] kboResults = crawlAllMonthsKbo(year);
        int total = 0;
        for (int month = SEASON_START_MONTH; month <= SEASON_END_MONTH; month++) {
            int idx      = month - SEASON_START_MONTH;
            int kboCount = (kboResults != null) ? kboResults[idx] : -1;
            if (kboCount >= 0) {
                // 0건은 비시즌/일정 없음을 의미 — 네이버 fallback 없이 그대로 반환
                log.info("[경기 크롤러] {}-{}: {}건 저장 [KBO]", year, String.format("%02d", month), kboCount);
                total += kboCount;
            } else {
                // -1: KBO 사이트 자체 접근 실패 시에만 네이버 fallback
                log.info("[경기 크롤러] KBO {}월 접근 실패 → 네이버 재시도", month);
                int naverCount = crawlMonthNaver(year, month);
                log.info("[경기 크롤러] {}-{}: {}건 저장 [NAVER]", year, String.format("%02d", month), naverCount);
                total += naverCount;
            }
        }
        log.info("[경기 크롤러] ===== {}년 시즌 전체 완료 - 합계 {}건 =====", year, total);
        return total;
    }

    /**
     * 지정 연·월 크롤링.
     * 1차: KBO 공식 사이트 (Selenium) → 2차: 네이버 스포츠 (Selenium, fallback)
     * KBO에서 1건 이상 수집하면 그걸 사용, 0건 이하일 때만 네이버 시도.
     */
    public int crawlMonth(int year, int month, String seriesIds) {
        log.info("[경기 크롤러] ===== {}년 {}월 크롤링 시작 [seriesIds={}] =====",
                 year, month, seriesIds);

        int result = crawlMonthKbo(year, month, seriesIds);
        if (result >= 0) {
            // 0건은 비시즌/일정 없음을 의미 — 네이버 fallback 없이 그대로 반환
            log.info("[경기 크롤러] ===== {}년 {}월 완료 [KBO] - {}건 저장 =====", year, month, result);
            return result;
        }

        // -1: KBO 사이트 자체 접근 실패 시에만 네이버 fallback
        log.info("[경기 크롤러] KBO 접근 실패 → 네이버 스포츠로 재시도");
        result = crawlMonthNaver(year, month);
        log.info("[경기 크롤러] ===== {}년 {}월 완료 [NAVER] - {}건 저장 =====", year, month, result);
        return result;
    }

    /**
     * 시점/월에 따라 적절한 seriesId 결정.
     * - 과거 시즌: 모든 시리즈 포함 ("0,9,6")
     * - 미래 시즌: 정규시즌만 ("0")
     * - 현재 시즌: 9월 이하면 정규시즌만, 10월 이상이면 현재 시점 기준으로 판단
     */
    private String resolveSeriesIds(int year, int month) {
        LocalDate now = LocalDate.now();
        if (year < now.getYear()) return "0,9,6";       // 과거 시즌: 전체 포함
        if (year > now.getYear()) return "0";            // 미래 시즌: 정규시즌만
        if (month <= 9)           return "0";            // 정규시즌 진행 중
        return (now.getMonthValue() >= 10) ? "0,9,6" : "0"; // 10월+ 가을야구 결정
    }

    // ── KBO 공식 사이트 (Selenium) ───────────────────────────────

    /**
     * KBO 공식 사이트에서 월별 일정을 Selenium으로 파싱·저장.
     * ASP.NET 동적 페이지이므로 ChromeDriver로 렌더링 후 소스를 Jsoup으로 파싱.
     * @return 저장 건수. 0이면 빈 달(비시즌), -1이면 접근 실패(fallback 신호).
     */
    private int crawlMonthKbo(int year, int month, String seriesIds) {
        String gameMonth = String.format("%02d", month);
        String url = String.format(
            "%s?seriesId=%s&teamId=&leagueId=1&seasonId=%d&gameMonth=%s",
            KBO_SCHEDULE_BASE, seriesIds, year, gameMonth);
        log.info("[경기 크롤러] [KBO] Selenium 페이지 로드: gameMonth={}, seriesIds={}", gameMonth, seriesIds);

        WebDriver driver = null;
        try {
            driver = createDriver();
            driver.get(url);

            // ASP.NET 테이블 렌더링 대기 (최대 10초)
            waitForKboTable(driver);

            String pageSource = driver.getPageSource();
            log.info("[경기 크롤러] [KBO] 소스 길이: {}자", pageSource.length());

            Document doc = Jsoup.parse(pageSource);
            List<Game> games = parseKboTable(doc, year, month);
            log.info("[경기 크롤러] [KBO] 파싱 결과: {}건", games.size());

            if (games.isEmpty()) return 0;

            int saved = 0;
            for (Game game : games) {
                try {
                    gameMapper.upsert(game);
                    saved++;
                    log.debug("[경기 크롤러] [KBO] 저장: {} {} vs {}",
                              game.getGameDate(), game.getAwayTeam(), game.getHomeTeam());
                } catch (Exception e) {
                    log.warn("[경기 크롤러] [KBO] DB 저장 실패 ({} vs {}): {}",
                             game.getAwayTeam(), game.getHomeTeam(), e.getMessage());
                }
            }
            return saved;

        } catch (Exception e) {
            log.warn("[경기 크롤러] [KBO] Selenium 접근 실패: {}", e.getMessage());
            return -1;
        } finally {
            if (driver != null) try { driver.quit(); } catch (Exception ignored) {}
        }
    }

    /**
     * KBO 공식 사이트에서 3~11월 전체를 단일 드라이버로 순회.
     * seriesId 변경 시에만 드라이버 재시작, 같은 seriesId 구간은 드롭다운으로 월 전환.
     * @return 월별 저장 건수 배열 (인덱스 0=3월, ...). 접속 실패 시 null.
     */
    private int[] crawlAllMonthsKbo(int year) {
        int size = SEASON_END_MONTH - SEASON_START_MONTH + 1;
        int[] results = new int[size];
        Arrays.fill(results, -1);
        WebDriver driver = null;
        String currentSeriesIds = null;
        try {
            for (int month = SEASON_START_MONTH; month <= SEASON_END_MONTH; month++) {
                int    idx       = month - SEASON_START_MONTH;
                String monthStr  = String.format("%02d", month);
                String seriesIds = resolveSeriesIds(year, month);
                log.info("[경기 크롤러] [KBO] 월 처리: {}-{}, seriesIds={}", year, monthStr, seriesIds);
                try {
                    // seriesId 변경 또는 드라이버 없으면 재시작
                    if (driver == null || !seriesIds.equals(currentSeriesIds)) {
                        if (driver != null) try { driver.quit(); } catch (Exception i) {}
                        String url = String.format(
                            "%s?seriesId=%s&teamId=&leagueId=1&seasonId=%d",
                            KBO_SCHEDULE_BASE, seriesIds, year);
                        driver = createDriver();
                        driver.get(url);
                        waitForKboTable(driver);
                        currentSeriesIds = seriesIds;
                        log.info("[경기 크롤러] [KBO] 새 드라이버 초기 페이지 로드 완료: {}", url);
                    }
                    // ASP.NET은 URL gameMonth 파라미터를 무시하므로 항상 드롭다운으로 월 선택
                    {
                        WebElement oldTable = null;
                        try { oldTable = driver.findElement(By.cssSelector("#tblScheduleList, #tblSchedule")); }
                        catch (Exception i) {}
                        selectKboMonth(driver, month);
                        if (oldTable != null) {
                            try {
                                new WebDriverWait(driver, Duration.ofSeconds(WAIT_SECONDS))
                                    .until(ExpectedConditions.stalenessOf(oldTable));
                            } catch (Exception i) {}
                        }
                        waitForKboTable(driver);
                    }
                    String pageSource = driver.getPageSource();
                    Document doc = Jsoup.parse(pageSource);
                    List<Game> games = parseKboTable(doc, year, month);
                    log.info("[경기 크롤러] [KBO] {}-{}: {}건 파싱", year, monthStr, games.size());
                    int saved = 0;
                    for (Game game : games) {
                        try { gameMapper.upsert(game); saved++; }
                        catch (Exception e) {
                            log.warn("[경기 크롤러] [KBO] DB 저장 실패 ({} vs {}): {}",
                                     game.getAwayTeam(), game.getHomeTeam(), e.getMessage());
                        }
                    }
                    results[idx] = saved;
                    log.info("[경기 크롤러] [KBO] {}-{}: {}건 저장 완료", year, monthStr, saved);
                } catch (Exception e) {
                    log.warn("[경기 크롤러] [KBO] {}월 처리 실패: {}", month, e.getMessage());
                    if (driver != null) { try { driver.quit(); } catch (Exception i) {} driver = null; currentSeriesIds = null; }
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[경기 크롤러] [KBO] 전체 처리 실패: {}", e.getMessage());
            return null;
        } finally {
            if (driver != null) try { driver.quit(); } catch (Exception i) {}
        }
    }

    // KBO ASP.NET 테이블 렌더링 대기 (PlayerCrawler의 WebDriverWait 패턴과 동일)
    private void waitForKboTable(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SECONDS));
        // 실제 확인된 KBO 일정 테이블 ID: tblScheduleList (tblSchedule 아님)
        for (String sel : new String[]{"#tblScheduleList", "#tblSchedule", "table.tbl-type06", "table.tbl"}) {
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(sel)));
                log.info("[경기 크롤러] [KBO] 테이블 렌더링 완료 ({})", sel);
                return;
            } catch (Exception ignored) {}
        }
        log.warn("[경기 크롤러] [KBO] 테이블 대기 타임아웃 - 현재 상태로 진행");
    }

    private void selectKboMonth(WebDriver driver, int month) {
        String monthStr = String.format("%02d", month);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SECONDS));
        try {
            WebElement dropdown = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("select[id*=Month], select[name*=Month]")
            ));
            new Select(dropdown).selectByValue(monthStr);
            log.info("[경기 크롤러] [KBO] 드롭다운 월 선택: {}", monthStr);
        } catch (Exception e) {
            log.warn("[경기 크롤러] [KBO] 월 드롭다운 미발견 ({}): {}", monthStr, e.getMessage());
            for (WebElement s : driver.findElements(By.tagName("select"))) {
                log.info("[경기 크롤러] [KBO] select 발견: id={}, name={}",
                         s.getAttribute("id"), s.getAttribute("name"));
            }
            throw e;
        }
    }

    // ── 네이버 스포츠 (Selenium) ─────────────────────────────────

    private int crawlMonthNaver(int year, int month) {
        int saved = 0;
        WebDriver driver = null;
        try {
            driver = createDriver();
            String url = buildNaverUrl(year, month);
            log.info("[경기 크롤러] [NAVER] URL: {}", url);
            driver.get(url);
            waitForRender(driver);

            String pageSource = driver.getPageSource();
            log.info("[경기 크롤러] [NAVER] 소스 길이: {}chars", pageSource.length());

            if (pageSource.length() < MIN_SOURCE_LEN) {
                log.error("[경기 크롤러] [NAVER] 소스 너무 짧음 ({}자). SPA 미렌더링 가능성.",
                          pageSource.length());
                return 0;
            }

            Document doc = Jsoup.parse(pageSource);
            logSelectorDebug(doc);

            List<Game> games = parseGames(doc, year, month);
            log.info("[경기 크롤러] [NAVER] 파싱 결과: {}건", games.size());

            for (Game game : games) {
                try {
                    gameMapper.upsert(game);
                    saved++;
                    log.debug("[경기 크롤러] [NAVER] 저장: {} {} vs {}",
                              game.getGameDate(), game.getAwayTeam(), game.getHomeTeam());
                } catch (Exception e) {
                    log.warn("[경기 크롤러] [NAVER] DB 저장 실패 ({} vs {}): {}",
                             game.getAwayTeam(), game.getHomeTeam(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[경기 크롤러] [NAVER] 예외: {}", e.getMessage(), e);
        } finally {
            if (driver != null) try { driver.quit(); } catch (Exception ignored) {}
        }
        return saved;
    }

    private String buildNaverUrl(int year, int month) {
        LocalDate now = LocalDate.now();
        if (year == now.getYear() && month == now.getMonthValue()) {
            return NAVER_SCHEDULE_URL;
        }
        return NAVER_SCHEDULE_URL + String.format("?date=%04d%02d01", year, month);
    }

    // ── React SPA 렌더링 대기 ────────────────────────────────────

    private void waitForRender(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SECONDS));
        for (String selector : CONTAINER_SELECTORS) {
            try {
                wait.until(d -> !d.findElements(By.cssSelector(selector)).isEmpty());
                log.info("[경기 크롤러] [NAVER] 렌더링 완료 ({})", selector);
                return;
            } catch (Exception ignored) {}
        }
        log.info("[경기 크롤러] [NAVER] 컨테이너 미발견, 소스 길이 기반 대기");
        try {
            wait.until(d -> d.getPageSource().length() > MIN_SOURCE_LEN);
            log.info("[경기 크롤러] [NAVER] 소스 길이 기준 렌더링 완료");
        } catch (Exception e) {
            log.warn("[경기 크롤러] [NAVER] 렌더링 대기 타임아웃 - 현재 상태로 진행");
        }
    }

    // ── Naver SPA 파싱 ────────────────────────────────────────────

    private List<Game> parseGames(Document doc, int year, int month) {
        List<Game> result = new ArrayList<>();

        // 전략 1: 날짜 섹션 단위
        for (String dateSel : DATE_ITEM_SELECTORS) {
            Elements dateSections = doc.select(dateSel);
            if (!dateSections.isEmpty()) {
                log.info("[경기 크롤러] [전략1] match_list_group {}개 발견 ({})", dateSections.size(), dateSel);
                for (Element section : dateSections) {
                    result.addAll(parseDateSection(section, year, month));
                }
                if (!result.isEmpty()) return result;
            }
        }

        // 전략 2: 경기 항목 직접 탐색
        for (String gameSel : GAME_ITEM_SELECTORS) {
            Elements gameItems = doc.select(gameSel);
            if (!gameItems.isEmpty()) {
                log.info("[경기 크롤러] [전략2] 경기 항목 {}개 ({})", gameItems.size(), gameSel);
                LocalDate currentDate = inferDateNearItem(gameItems.first(), year, month);
                for (Element item : gameItems) {
                    try {
                        LocalDate nearby = inferDateNearItem(item, year, month);
                        if (nearby != null) currentDate = nearby;
                        Game game = parseGameItem(item, currentDate != null ? currentDate : LocalDate.of(year, month, 1));
                        if (game != null) result.add(game);
                    } catch (Exception e) {
                        log.warn("[경기 크롤러] 항목 파싱 실패: {}", e.getMessage());
                    }
                }
                if (!result.isEmpty()) return result;
            }
        }

        // 전략 3: 테이블 fallback
        log.warn("[경기 크롤러] [전략3] SPA 구조 미발견, 테이블 파싱 시도");
        return parseTable(doc, year, month);
    }

    private List<Game> parseDateSection(Element section, int year, int month) {
        List<Game> result = new ArrayList<>();
        LocalDate date = extractDateFromSection(section, year, month);
        if (date == null) {
            log.debug("[경기 크롤러] 날짜 추출 실패: {}",
                      section.text().substring(0, Math.min(50, section.text().length())));
            return result;
        }
        for (String gameSel : GAME_ITEM_SELECTORS) {
            Elements items = section.select(gameSel);
            if (!items.isEmpty()) {
                log.info("[경기 크롤러] 날짜 그룹 발견: {} (경기 {}개)", date, items.size());
                for (Element item : items) {
                    try {
                        Game game = parseGameItem(item, date);
                        if (game != null) result.add(game);
                    } catch (Exception e) {
                        log.warn("[경기 크롤러] 경기 항목 파싱 실패 ({}): {}", date, e.getMessage());
                    }
                }
                return result;
            }
        }
        Game game = parseGameItem(section, date);
        if (game != null) result.add(game);
        return result;
    }

    private LocalDate extractDateFromSection(Element section, int year, int month) {
        for (String sel : new String[]{
                "[class*=ScheduleAllType_group_title__]",
                "[class*=date__]", "[class*=date_text]", "[class*=day__]", ".date", "time"}) {
            Element el = section.selectFirst(sel);
            if (el != null) {
                LocalDate d = parseDate(el.text(), year, month);
                if (d != null) return d;
            }
        }
        return parseDate(section.text(), year, month);
    }

    private Game parseGameItem(Element el, LocalDate date) {
        List<String> teams = extractTeamNames(el);
        if (teams.size() < 2) return null;

        String awayTeam = normalizeTeam(teams.get(0));
        String homeTeam = normalizeTeam(teams.get(teams.size() - 1));
        if (awayTeam.isBlank() || homeTeam.isBlank() || awayTeam.equals(homeTeam)) return null;

        LocalTime gameTime = extractGameTime(el);

        Integer awayScore = null, homeScore = null;
        GameStatus status = GameStatus.SCHEDULED;

        List<Integer> scores = extractScores(el);
        if (scores.size() >= 2) {
            awayScore = scores.get(0);
            homeScore = scores.get(scores.size() - 1);
            status = GameStatus.FINISHED;
        }

        String fullText = el.text();
        if (fullText.contains("취소") || fullText.contains("CANCEL")) status = GameStatus.CANCELED;
        else if (fullText.contains("연기") || fullText.contains("PPD")) status = GameStatus.POSTPONED;

        String stadium = extractStadium(el);

        return Game.builder()
                .gameDate(date)
                .gameTime(gameTime)
                .awayTeam(awayTeam)
                .homeTeam(homeTeam)
                .awayScore(awayScore)
                .homeScore(homeScore)
                .status(status)
                .stadium(stadium)
                .build();
    }

    // ── 항목 내 세부 추출 ────────────────────────────────────────

    private List<String> extractTeamNames(Element el) {
        List<String> names = new ArrayList<>();
        for (String sel : TEAM_NAME_SELECTORS) {
            Elements found = el.select(sel);
            for (Element e : found) {
                String text = e.ownText().trim();
                if (!text.isBlank() && isKboTeam(text)) names.add(text);
            }
            if (names.size() >= 2) return names;
        }
        for (Element e : el.select("strong, b, em")) {
            String text = e.ownText().trim();
            if (isKboTeam(text)) names.add(text);
        }
        return names;
    }

    private List<Integer> extractScores(Element el) {
        List<Integer> scores = new ArrayList<>();
        for (String sel : SCORE_SELECTORS) {
            Elements found = el.select(sel);
            for (Element e : found) {
                Integer v = parseInt(e.ownText());
                if (v != null) scores.add(v);
            }
            if (scores.size() >= 2) return scores;
        }
        String elText = el.text().replaceAll("\\d{1,2}:\\d{2}", ""); // 시간 패턴 제거
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})\\s*-\\s*(\\d{1,2})").matcher(elText);
        if (m.find()) {
            scores.add(Integer.parseInt(m.group(1)));
            scores.add(Integer.parseInt(m.group(2)));
        }
        return scores;
    }

    private LocalTime extractGameTime(Element el) {
        for (String sel : new String[]{"[class*=time__]", "[class*=game_time]", "time", "[class*=hour]"}) {
            Element e = el.selectFirst(sel);
            if (e != null) {
                LocalTime t = parseTime(e.text());
                if (t != null) return t;
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(el.text());
        if (m.find()) return LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        return LocalTime.of(18, 0);
    }

    private String extractStadium(Element el) {
        for (String sel : new String[]{"[class*=stadium]", "[class*=place]", "[class*=ground]", "[class*=venue]"}) {
            Element e = el.selectFirst(sel);
            if (e != null) return e.text().trim();
        }
        return null;
    }

    private LocalDate inferDateNearItem(Element item, int year, int month) {
        if (item == null) return null;
        Element parent = item.parent();
        while (parent != null && !parent.tagName().equals("body")) {
            for (String sel : new String[]{"[class*=date__]", "[class*=date_text]", "[class*=day_title]"}) {
                Element dateEl = parent.selectFirst(sel);
                if (dateEl != null) {
                    LocalDate d = parseDate(dateEl.text(), year, month);
                    if (d != null) return d;
                }
            }
            parent = parent.parent();
        }
        return null;
    }

    // ── KBO 공식 사이트 전용 테이블 파싱 ────────────────────────

    /**
     * KBO 공식 사이트 일정 테이블 파싱.
     * 구조: 날짜(rowspan) | 시간 | 경기("팀A NvsM 팀B" 또는 "팀A vs 팀B") | ... | 구장
     * 날짜 셀은 "05.13(화)" 형식으로 여러 행에 rowspan으로 걸침.
     */
    private List<Game> parseKboTable(Document doc, int year, int month) {
        List<Game> result = new ArrayList<>();

        // 실제 확인된 KBO 일정 테이블 ID: tblScheduleList
        Element table = doc.selectFirst("#tblScheduleList");
        if (table == null) table = doc.selectFirst("#tblSchedule");
        if (table == null) table = doc.selectFirst("table.tbl-type06");
        if (table == null) {
            log.warn("[경기 크롤러] [KBO] 테이블 미발견 (#tblScheduleList, #tblSchedule, table.tbl-type06)");
            logKboBodyPreview(doc);
            return result;
        }

        Elements rows = table.select("tbody tr");
        log.info("[경기 크롤러] [KBO] 테이블 발견, {}개 행 파싱", rows.size());

        LocalDate currentDate = null;
        for (Element row : rows) {
            try {
                Elements cells = row.select("td");
                if (cells.isEmpty()) continue;

                // 첫 번째 셀이 날짜 셀인지 확인 ("MM.DD(요일)" 패턴 또는 rowspan 속성)
                Element first = cells.first();
                String firstText = first != null ? first.text().trim() : "";
                boolean hasDateCell = first != null
                        && (first.hasAttr("rowspan")
                            || firstText.matches("\\d{1,2}\\.\\d{1,2}.*"));

                if (hasDateCell) {
                    LocalDate parsed = parseDate(firstText, year, month);
                    if (parsed != null) currentDate = parsed;
                }
                if (currentDate == null) continue;

                // offset: 날짜 셀 있으면 1, 없으면 0
                int off = hasDateCell ? 1 : 0;
                // 최소 시간 + 경기 셀 2개 필요
                if (cells.size() < off + 2) continue;

                LocalTime time   = parseTime(cells.get(off).text());
                // td.play 셀 직접 파싱 (text() 대신 HTML 구조 사용)
                Element playCell = row.selectFirst("td.play");
                // 구장은 마지막 셀 (날짜|시간|경기|게임센터|하이라이트|TV|라디오|구장)
                Element lastCell = cells.last();
                String stadium   = (lastCell != null && cells.size() > off + 2)
                                   ? lastCell.text().trim() : null;

                Game game = parseKboPlayCell(playCell, currentDate, time, stadium);
                if (game != null) result.add(game);

            } catch (Exception e) {
                log.warn("[경기 크롤러] [KBO] 행 파싱 실패: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * KBO .play 셀을 HTML 구조로 직접 파싱 (text() 대신 DOM 탐색).
     * 종료: <span>원정팀</span><em><span>점수</span><span>vs</span><span>점수</span></em><span>홈팀</span>
     * 예정: <span>원정팀</span><span>vs</span><span>홈팀</span>  또는 em 없이 vs만
     */
    private Game parseKboPlayCell(Element playCell, LocalDate date, LocalTime time, String stadium) {
        if (playCell == null) return null;

        String fullText = playCell.text();
        GameStatus status = GameStatus.SCHEDULED;
        if (fullText.contains("취소"))  status = GameStatus.CANCELED;
        else if (fullText.contains("연기")) status = GameStatus.POSTPONED;

        // 직계 <span> 자식 (em 밖) → 원정팀(첫 번째), 홈팀(마지막)
        List<Element> directSpans = new ArrayList<>();
        for (Element child : playCell.children()) {
            if ("span".equals(child.tagName())) directSpans.add(child);
        }
        if (directSpans.size() < 2) return null;

        String awayTeam = normalizeTeam(directSpans.get(0).text().trim());
        String homeTeam = normalizeTeam(directSpans.get(directSpans.size() - 1).text().trim());
        if (awayTeam.isBlank() || homeTeam.isBlank() || awayTeam.equals(homeTeam)) return null;

        // <em> 안의 <span> → 점수 (순서: 원정점수, "vs", 홈점수)
        Integer awayScore = null, homeScore = null;
        Element em = playCell.selectFirst("em");
        if (em != null) {
            Elements emSpans = em.select("span");
            if (emSpans.size() >= 3) {
                try { awayScore = Integer.parseInt(emSpans.get(0).text().trim()); } catch (Exception ignored) {}
                try { homeScore = Integer.parseInt(emSpans.get(2).text().trim()); } catch (Exception ignored) {}
                if (awayScore != null && homeScore != null) status = GameStatus.FINISHED;
            }
        }

        return Game.builder()
                .gameDate(date)
                .gameTime(time != null ? time : LocalTime.of(18, 0))
                .awayTeam(awayTeam)
                .homeTeam(homeTeam)
                .awayScore(awayScore)
                .homeScore(homeScore)
                .status(status)
                .stadium(stadium)
                .build();
    }

    private void logKboBodyPreview(Document doc) {
        // 전체 table 요소 목록 출력 (selector 디버깅)
        Elements tables = doc.select("table");
        log.info("[경기 크롤러] [KBO] 발견된 table 수: {}개", tables.size());
        for (int i = 0; i < Math.min(tables.size(), 5); i++) {
            Element t = tables.get(i);
            log.info("[경기 크롤러] [KBO] table[{}] id='{}' class='{}'",
                     i, t.id(), t.className());
        }
        // body 앞부분 미리보기 (1000자)
        Element body = doc.body();
        if (body != null) {
            String html = body.html();
            log.info("[경기 크롤러] [KBO] body html 앞 1000자:\n{}",
                     html.substring(0, Math.min(1000, html.length())));
        }
    }

    // ── 테이블 파싱 (Naver fallback용) ───────────────────────────

    /**
     * 일반 schedule 테이블 구조 파싱 (네이버 SPA 전략3 fallback).
     * 날짜(rowspan) | 시간 | 원정팀 | 결과 | 홈팀 | 경기장
     */
    private List<Game> parseTable(Document doc, int year, int month) {
        List<Game> result = new ArrayList<>();

        // KBO 공식 사이트 우선, 그 외 일반 schedule 테이블
        Element table = doc.selectFirst("#tblSchedule");
        if (table == null) table = doc.selectFirst("table.tbl-type06");
        if (table == null) table = doc.selectFirst("table[class*=schedule]");

        if (table == null) {
            log.warn("[경기 크롤러] 테이블 미발견. 페이지 구조를 수동 확인하세요.");
            Element body = doc.body();
            if (body != null) log.info("[경기 크롤러] body 시작 200자: {}",
                body.text().substring(0, Math.min(200, body.text().length())));
            return result;
        }

        log.info("[경기 크롤러] 테이블 발견 (id={}, class={}), 행 파싱 시작",
                 table.id(), table.className());

        LocalDate currentDate = null;
        for (Element row : table.select("tbody tr")) {
            try {
                Elements cells = row.select("td");
                if (cells.isEmpty()) continue;

                // 날짜 셀: rowspan 속성 또는 date 클래스
                Element dateCell = row.selectFirst("td[rowspan], td.td-date, td.date");
                if (dateCell != null) {
                    LocalDate parsed = parseDate(dateCell.text(), year, month);
                    if (parsed != null) currentDate = parsed;
                }
                if (currentDate == null) continue;

                // date 셀이 첫 번째 셀이면 offset 1, 아니면 0
                int off = (dateCell != null && cells.first() == dateCell) ? 1 : 0;
                if (cells.size() < off + 4) continue;

                LocalTime time    = parseTime(cells.get(off).text());
                String away       = normalizeTeam(cells.get(off + 1).text().trim());
                String scoreRaw   = cells.get(off + 2).text().trim();
                String home       = normalizeTeam(cells.get(off + 3).text().trim());
                String stadium    = cells.size() > off + 4 ? cells.get(off + 4).text().trim() : null;

                if (away.isBlank() || home.isBlank()) continue;

                Integer awayScore = null, homeScore = null;
                GameStatus status = GameStatus.SCHEDULED;

                String scoreOnly = scoreRaw.replaceAll("\\d{1,2}:\\d{2}", ""); // 시간 패턴 제거
                java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(\\d{1,2})\\s*-\\s*(\\d{1,2})").matcher(scoreOnly);
                if (m.find()) {
                    awayScore = Integer.parseInt(m.group(1));
                    homeScore = Integer.parseInt(m.group(2));
                    status = GameStatus.FINISHED;
                } else if (scoreRaw.contains("취소")) {
                    status = GameStatus.CANCELED;
                } else if (scoreRaw.contains("연기")) {
                    status = GameStatus.POSTPONED;
                }

                result.add(Game.builder()
                        .gameDate(currentDate)
                        .gameTime(time != null ? time : LocalTime.of(18, 0))
                        .awayTeam(away)
                        .homeTeam(home)
                        .awayScore(awayScore)
                        .homeScore(homeScore)
                        .status(status)
                        .stadium(stadium)
                        .build());

            } catch (Exception e) {
                log.warn("[경기 크롤러] 테이블 행 파싱 실패: {}", e.getMessage());
            }
        }
        return result;
    }

    // ── 유틸 ─────────────────────────────────────────────────────

    private void logSelectorDebug(Document doc) {
        String[] debugSelectors = {
            "[class*=ScheduleAllType_match_list_group__]",
            "[class*=ScheduleAllType_group_title__]",
            "[class*=MatchBox_match_item__]",
            "[class*=ScheduleAllType]", "[class*=ScheduleLeagueType]",
            "[class*=match_list]", "[class*=match_item]",
            "[class*=schedule]", "[class*=game_list]", "[class*=date__]",
            "table", "ul", "li",
        };
        log.info("[경기 크롤러] ─── 선택자 히트 수 ───");
        for (String sel : debugSelectors) {
            int cnt = doc.select(sel).size();
            if (cnt > 0) log.info("[경기 크롤러]   {} → {}개", sel, cnt);
        }
        log.info("[경기 크롤러] ────────────────────");
    }

    private LocalDate parseDate(String text, int year, int month) {
        if (text == null || text.isBlank()) return null;
        try {
            // 한글 패턴 우선: "5월 13일", "5월13일 (화)" 등
            java.util.regex.Matcher km =
                java.util.regex.Pattern.compile("(\\d+)월\\s*(\\d+)일").matcher(text);
            if (km.find()) {
                int mm = Integer.parseInt(km.group(1));
                int dd = Integer.parseInt(km.group(2));
                if (mm >= 1 && mm <= 12 && dd >= 1 && dd <= 31) {
                    return LocalDate.of(year, mm, dd);
                }
            }
            // 숫자 추출 방식 fallback
            String digits = text.replaceAll("[^0-9]", " ").trim();
            String[] parts = digits.split("\\s+");
            if (parts.length >= 3) {
                int a = Integer.parseInt(parts[0]);
                int b = Integer.parseInt(parts[1]);
                int c = Integer.parseInt(parts[2]);
                if (a > 2000) return LocalDate.of(a, b, c);
                if (b > 0 && b <= 12 && c > 0 && c <= 31) return LocalDate.of(year, b, c);
            }
            if (parts.length >= 2) {
                int a = Integer.parseInt(parts[0]);
                int b = Integer.parseInt(parts[1]);
                if (a >= 1 && a <= 12 && b >= 1 && b <= 31) return LocalDate.of(year, a, b);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private LocalTime parseTime(String text) {
        if (text == null) return null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(text);
            if (m.find()) return LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        } catch (Exception ignored) {}
        return null;
    }

    private Integer parseInt(String text) {
        if (text == null) return null;
        try { return Integer.parseInt(text.trim().replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return null; }
    }

    private static final List<String> KBO_TEAM_NAMES = List.of(
        "두산", "LG", "SSG", "키움", "삼성", "KT", "롯데", "한화", "NC", "KIA",
        "엘지", "랜더스", "케이티", "기아", "엔씨", "베어스", "트윈스", "이글스", "라이온즈", "히어로즈"
    );

    private boolean isKboTeam(String text) {
        return KBO_TEAM_NAMES.stream().anyMatch(text::contains);
    }

    private String normalizeTeam(String raw) {
        if (raw == null) return "";
        return switch (raw.trim()) {
            case "두산", "두산 베어스", "베어스"           -> "두산";
            case "LG",   "엘지", "LG 트윈스", "트윈스"    -> "LG";
            case "SSG",  "랜더스", "SSG 랜더스"           -> "SSG";
            case "키움", "히어로즈", "키움 히어로즈"        -> "키움";
            case "삼성", "라이온즈", "삼성 라이온즈"        -> "삼성";
            case "KT",   "케이티", "KT 위즈", "위즈"      -> "KT";
            case "롯데", "자이언츠", "롯데 자이언츠"        -> "롯데";
            case "한화", "이글스", "한화 이글스"            -> "한화";
            case "NC",   "엔씨", "NC 다이노스", "다이노스"  -> "NC";
            case "KIA",  "기아", "KIA 타이거즈", "타이거즈" -> "KIA";
            default -> raw.trim();
        };
    }

    private WebDriver createDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--lang=ko-KR,ko;q=0.9");
        options.addArguments("user-agent=" + USER_AGENT);
        options.addArguments("--blink-settings=imagesEnabled=false");

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2);
        prefs.put("profile.managed_default_content_settings.stylesheets", 2);
        options.setExperimentalOption("prefs", prefs);

        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        return driver;
    }
}
