package com.kbo.stats.service;

import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlingService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36";

    private static final int CURRENT_SEASON = 2026;
    private static final long TABLE_WAIT_SECONDS = 10;
    private static final long PAGE_DELAY_MS = 300;

    private static final String KBO_BATTER_URL =
            "https://www.koreabaseball.com/Record/Player/HitterBasic/Basic1.aspx";
    private static final String KBO_BATTER_STEAL_URL =
            "https://www.koreabaseball.com/Record/Player/Runner/Basic.aspx";
    private static final String KBO_PITCHER_URL =
            "https://www.koreabaseball.com/Record/Player/PitcherBasic/Basic1.aspx";
    private static final String KBO_PITCHER_SAVE_URL =
            "https://www.koreabaseball.com/Record/Player/PitcherBasic/Basic1.aspx?sort=SV_CN";

    private static final String TABLE_ROW_SEL = "table.tData01 tbody tr";
    private static final String PAGER_LINK_SEL = "div.paging a";

    private final PlayerService playerService;

    public void crawlAll() {
        log.info("=== KBO 크롤링 시작 (시즌: {}) ===", CURRENT_SEASON);
        playerService.deleteAll();
        WebDriver driver = null;
        int batterCount = 0, pitcherCount = 0;
        StopWatch sw = new StopWatch("KBO 크롤링");
        try {
            sw.start("드라이버 생성");
            driver = createDriver();
            sw.stop();

            sw.start("타자 크롤링");
            batterCount  = crawlKbo(driver, KBO_BATTER_URL,  PlayerType.BATTER,  "타자");
            sw.stop();

            sw.start("도루 크롤링");
            crawlBatterStolenBases(driver);
            sw.stop();

            sw.start("투수 크롤링");
            pitcherCount = crawlKbo(driver, KBO_PITCHER_URL, PlayerType.PITCHER, "투수");
            sw.stop();

            sw.start("세이브/홀드 크롤링");
            crawlSavesAndHolds(driver);
            sw.stop();
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
            if (sw.isRunning()) sw.stop();
            log.info("\n{}", sw.prettyPrint());
            log.info("총 소요시간: {}초", String.format("%.1f", sw.getTotalTimeSeconds()));
        }
        log.info("=== KBO 크롤링 완료 | 타자: {}명, 투수: {}명 ===", batterCount, pitcherCount);
    }

    private int crawlKbo(WebDriver driver, String url, PlayerType playerType, String label) {
        List<Player> players = new ArrayList<>();
        try {
            log.info("[{}] 브라우저 이동 → {}", label, url);
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TABLE_WAIT_SECONDS));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(TABLE_ROW_SEL)));
            Thread.sleep(PAGE_DELAY_MS);

            selectSeason(driver, wait, label);

            if (playerType == PlayerType.PITCHER) {
                selectAllPitchers(driver, wait);
            }

            for (int page = 1; ; page++) {
                int collected = collectPage(driver, playerType, label, page, players);
                log.info("[{}] 페이지 {} 수집: {}명 (누적: {}명)", label, page, collected, players.size());

                if (!goToNextPage(driver, wait)) {
                    log.info("[{}] 마지막 페이지 ({}) → 종료", label, page);
                    break;
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] 인터럽트 → 크롤링 중단 (수집: {}명)", label, players.size());
        } catch (Exception e) {
            log.error("[{}] 크롤링 실패: {} (수집: {}명)", label, e.getMessage(), players.size(), e);
        }
        return players.size();
    }

    private int collectPage(WebDriver driver, PlayerType playerType,
                            String label, int page, List<Player> players) {
        Document doc = Jsoup.parse(driver.getPageSource());
        Elements rows = filterDataRows(doc.select(TABLE_ROW_SEL));
        if (rows.isEmpty()) {
            log.warn("[{}] 페이지 {}에서 테이블 행을 찾지 못함", label, page);
            return 0;
        }

        int count = 0;
        for (Element row : rows) {
            try {
                Player p = playerType == PlayerType.BATTER
                        ? parseKboBatterRow(row)
                        : parseKboPitcherRow(row);
                if (p != null) {
                    players.add(p);
                    playerService.saveOrUpdate(p);
                    count++;
                }
            } catch (Exception e) {
                log.warn("[{}] 행 파싱 실패: {}", label, e.getMessage());
            }
        }
        return count;
    }

    // td 없는 행, th 포함 행, 5열 미만, 첫 셀이 숫자(순위)가 아닌 행 제거
    private Elements filterDataRows(Elements rows) {
        Elements result = new Elements();
        for (Element row : rows) {
            Elements tds = row.select("td");
            if (tds.isEmpty()) continue;
            if (!row.select("th").isEmpty()) continue;
            if (tds.size() < 5) continue;
            if (!isNumeric(tds.first().text().trim())) continue;
            result.add(row);
        }
        return result;
    }

    private boolean goToNextPage(WebDriver driver, WebDriverWait wait) throws InterruptedException {
        String prevFirstRow;
        try {
            prevFirstRow = driver.findElement(By.cssSelector(TABLE_ROW_SEL)).getText().trim();
        } catch (Exception e) {
            return false;
        }

        int currentPage = getCurrentPageNumber(driver);
        WebElement nextBtn = findPageNumberLink(driver, currentPage + 1);
        if (nextBtn == null) {
            nextBtn = findNextGroupButton(driver);
        }
        if (nextBtn == null) {
            return false;
        }

        try {
            nextBtn.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextBtn);
        }

        try {
            wait.until(d -> {
                try {
                    String cur = d.findElement(By.cssSelector(TABLE_ROW_SEL)).getText().trim();
                    return !cur.isEmpty() && !cur.equals(prevFirstRow);
                } catch (Exception ex) {
                    return false;
                }
            });
        } catch (Exception e) {
            Thread.sleep(PAGE_DELAY_MS);
        }
        return true;
    }

    // 현재 페이지: div.paging 내 class="on" 링크의 텍스트
    private int getCurrentPageNumber(WebDriver driver) {
        try {
            WebElement active = driver.findElement(By.cssSelector("div.paging a.on"));
            String text = active.getText().trim();
            if (isNumeric(text)) return Integer.parseInt(text);
        } catch (Exception e) {
            log.debug("[페이지네이션] 현재 페이지 감지 실패: {}", e.getMessage());
        }
        return 1;
    }

    private WebElement findPageNumberLink(WebDriver driver, int pageNumber) {
        String target = String.valueOf(pageNumber);
        try {
            for (WebElement link : driver.findElements(By.cssSelector(PAGER_LINK_SEL))) {
                if (target.equals(link.getText().trim())) return link;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private WebElement findNextGroupButton(WebDriver driver) {
        try {
            for (WebElement link : driver.findElements(By.cssSelector(PAGER_LINK_SEL))) {
                String text = link.getText().trim();
                if (">".equals(text) || "다음".equals(text) || "▶".equals(text)) return link;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // 타자 컬럼: 순위(0) 선수명(1) 팀명(2) AVG(3) G(4) PA(5) AB(6) R(7) H(8) 2B(9) 3B(10) HR(11) TB(12) RBI(13)
    private Player parseKboBatterRow(Element row) {
        Elements cols = row.select("td");
        if (cols.size() < 14) return null;

        String name = extractName(cols.get(1));
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

    // 투수 컬럼: 순위(0) 선수명(1) 팀명(2) ERA(3) G(4) W(5) L(6) SV(7) HLD(8) WPCT(9)
    private Player parseKboPitcherRow(Element row) {
        Elements cols = row.select("td");
        if (cols.size() < 9) return null;

        String name = extractName(cols.get(1));
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
                .saves(parseInt(cols.get(7).text()))
                .holds(parseInt(cols.get(8).text()))
                .build();
    }

    // 도루 페이지 컬럼: 순위(0) 선수명(1) 팀명(2) G(3) SB(4) SBA(5) SBP(6) CS(7)
    private void crawlBatterStolenBases(WebDriver driver) {
        log.info("[도루] 크롤링 시작 → {}", KBO_BATTER_STEAL_URL);
        int count = 0;
        try {
            driver.get(KBO_BATTER_STEAL_URL);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TABLE_WAIT_SECONDS));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(TABLE_ROW_SEL)));
            Thread.sleep(PAGE_DELAY_MS);

            selectSeason(driver, wait, "도루");

            for (int page = 1; ; page++) {
                Document doc = Jsoup.parse(driver.getPageSource());
                Elements rows = filterDataRows(doc.select(TABLE_ROW_SEL));
                for (Element row : rows) {
                    try {
                        Elements cols = row.select("td");
                        if (cols.size() < 5) continue;
                        String name = extractName(cols.get(1));
                        String team = cols.get(2).text().trim();
                        Integer sb  = parseInt(cols.get(4).text());
                        if (!name.isBlank() && !team.isBlank() && sb != null) {
                            playerService.updateStolenBases(name, team, sb);
                            count++;
                        }
                    } catch (Exception e) {
                        log.warn("[도루] 행 파싱 실패: {}", e.getMessage());
                    }
                }
                log.info("[도루] 페이지 {} 처리 (누적: {}명)", page, count);
                if (!goToNextPage(driver, wait)) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[도루] 인터럽트 → 중단 (처리: {}명)", count);
        } catch (Exception e) {
            log.error("[도루] 크롤링 실패: {} (처리: {}명)", e.getMessage(), count, e);
        }
        log.info("[도루] 크롤링 완료 ({}명 업데이트)", count);
    }

    // 세이브 정렬 페이지에서 구원투수 세이브/홀드 upsert
    // 컬럼: 순위(0) 선수명(1) 팀명(2) ERA(3) G(4) W(5) L(6) SV(7) HLD(8)
    private void crawlSavesAndHolds(WebDriver driver) {
        log.info("[세이브/홀드] 크롤링 시작 (투수 페이지에서 SV 정렬)");
        int count = 0;
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TABLE_WAIT_SECONDS));

            // 현재 투수 페이지에서 SV 컬럼 헤더 클릭으로 정렬 변경
            boolean sorted = false;
            try {
                String prevFirstRow = driver.findElement(By.cssSelector(TABLE_ROW_SEL)).getText().trim();
                WebElement svLink = null;
                for (WebElement th : driver.findElements(By.cssSelector("table.tData01 thead th, table.tData01 th"))) {
                    if ("SV".equals(th.getText().trim())) {
                        try {
                            svLink = th.findElement(By.tagName("a"));
                        } catch (Exception e) {
                            svLink = th;
                        }
                        break;
                    }
                }
                if (svLink != null) {
                    try {
                        svLink.click();
                    } catch (Exception e) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", svLink);
                    }
                    wait.until(d -> {
                        try {
                            String cur = d.findElement(By.cssSelector(TABLE_ROW_SEL)).getText().trim();
                            return !cur.isEmpty() && !cur.equals(prevFirstRow);
                        } catch (Exception ex) {
                            return false;
                        }
                    });
                    sorted = true;
                    log.info("[세이브/홀드] SV 헤더 클릭으로 정렬 완료");
                } else {
                    log.warn("[세이브/홀드] SV 헤더를 찾지 못함 → fallback URL 사용");
                }
            } catch (Exception e) {
                log.warn("[세이브/홀드] SV 헤더 클릭 실패 → fallback URL 사용: {}", e.getMessage());
            }

            if (!sorted) {
                driver.get(KBO_PITCHER_SAVE_URL);
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(TABLE_ROW_SEL)));
                Thread.sleep(PAGE_DELAY_MS);
            }

            for (int page = 1; ; page++) {
                Document doc = Jsoup.parse(driver.getPageSource());
                Elements rows = filterDataRows(doc.select(TABLE_ROW_SEL));
                for (Element row : rows) {
                    try {
                        Elements cols = row.select("td");
                        if (cols.size() < 9) continue;
                        String name  = extractName(cols.get(1));
                        String team  = cols.get(2).text().trim();
                        Double era   = parseDouble(cols.get(3).text());
                        Integer games = parseInt(cols.get(4).text());
                        Integer wins  = parseInt(cols.get(5).text());
                        Integer saves = parseInt(cols.get(7).text());
                        Integer holds = parseInt(cols.get(8).text());
                        if (!name.isBlank() && !team.isBlank()) {
                            playerService.saveOrUpdateSavesHolds(name, team, saves, holds, era, games, wins);
                            count++;
                        }
                    } catch (Exception e) {
                        log.warn("[세이브/홀드] 행 파싱 실패: {}", e.getMessage());
                    }
                }
                log.info("[세이브/홀드] 페이지 {} 처리 (누적: {}명)", page, count);
                if (!goToNextPage(driver, wait)) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[세이브/홀드] 인터럽트 → 중단 (처리: {}명)", count);
        } catch (Exception e) {
            log.error("[세이브/홀드] 크롤링 실패: {} (처리: {}명)", e.getMessage(), count, e);
        }
        log.info("[세이브/홀드] 크롤링 완료 ({}명 upsert)", count);
    }

    // KBO ASP.NET 연도 드롭다운의 알려진 ClientID
    private static final String SEASON_DROPDOWN_ID =
            "cphContents_cphContents_cphContents_ddlSeason_ddlSeason";

    private void selectSeason(WebDriver driver, WebDriverWait wait, String label) throws InterruptedException {
        String targetYear = String.valueOf(CURRENT_SEASON);
        try {
            WebElement ddl = findYearDropdown(driver, targetYear, label);
            if (ddl == null) {
                log.warn("[{}] 연도 드롭다운 미발견 → 기본값으로 진행", label);
                return;
            }

            Select s = new Select(ddl);
            String current = s.getFirstSelectedOption().getText().trim();
            log.info("[{}] 연도 드롭다운 id={} 현재선택: {}", label, ddl.getAttribute("id"), current);

            if (targetYear.equals(current)) {
                log.info("[{}] {} 시즌 이미 선택됨", label, targetYear);
                return;
            }

            WebElement anchor = driver.findElement(By.cssSelector(TABLE_ROW_SEL));

            // Selenium Select로 선택하면 onchange → ASP.NET __doPostBack 자동 트리거
            try {
                s.selectByVisibleText(targetYear);
            } catch (Exception e) {
                log.warn("[{}] selectByVisibleText 실패 → selectByValue 재시도: {}", label, e.getMessage());
                s.selectByValue(targetYear);
            }

            try {
                wait.until(ExpectedConditions.stalenessOf(anchor));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(TABLE_ROW_SEL)));
            } catch (Exception ex) {
                log.warn("[{}] postback 대기 타임아웃 → {}ms 추가 대기", label, PAGE_DELAY_MS);
                Thread.sleep(PAGE_DELAY_MS);
            }
            Thread.sleep(PAGE_DELAY_MS);
            log.info("[{}] {} 시즌 선택 완료 (이전: {})", label, targetYear, current);

        } catch (Exception e) {
            log.warn("[{}] 연도 선택 실패 → 기본값으로 진행: {}", label, e.getMessage());
        }
    }

    private WebElement findYearDropdown(WebDriver driver, String targetYear, String label) {
        // 1. 알려진 ID로 먼저 시도
        try {
            WebElement el = driver.findElement(By.id(SEASON_DROPDOWN_ID));
            log.info("[{}] 연도 드롭다운 발견 by ID: {}", label, SEASON_DROPDOWN_ID);
            return el;
        } catch (Exception e) {
            log.debug("[{}] ID '{}' 로 드롭다운 못 찾음, 옵션 스캔으로 탐색", label, SEASON_DROPDOWN_ID);
        }

        // 2. targetYear 옵션을 포함한 select 탐색
        for (WebElement sel : driver.findElements(By.tagName("select"))) {
            try {
                Select s = new Select(sel);
                boolean hasYear = s.getOptions().stream()
                        .anyMatch(o -> targetYear.equals(o.getText().trim())
                                    || targetYear.equals(o.getAttribute("value")));
                if (hasYear) {
                    log.info("[{}] 연도 드롭다운 발견 by option scan: id={}", label, sel.getAttribute("id"));
                    return sel;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // 투수 페이지의 타입 필터(전체/선발/구원)를 "전체"로 설정
    private void selectAllPitchers(WebDriver driver, WebDriverWait wait) throws InterruptedException {
        try {
            // select 드롭다운에서 "전체" 옵션 탐색 (연도 드롭다운은 제외)
            for (WebElement sel : driver.findElements(By.tagName("select"))) {
                try {
                    Select s = new Select(sel);
                    List<WebElement> options = s.getOptions();

                    // 4자리 연도 옵션이 있는 드롭다운은 시즌 드롭다운이므로 건너뜀
                    boolean isYearDropdown = options.stream()
                            .anyMatch(o -> o.getText().trim().matches("\\d{4}"));
                    if (isYearDropdown) continue;

                    boolean has전체 = options.stream()
                            .anyMatch(o -> "전체".equals(o.getText().trim()));
                    if (!has전체) continue;

                    String current = s.getFirstSelectedOption().getText().trim();
                    if ("전체".equals(current)) {
                        log.info("[투수] 전체 필터 이미 선택됨");
                        return;
                    }
                    WebElement anchor = driver.findElement(By.cssSelector(TABLE_ROW_SEL));
                    s.selectByVisibleText("전체");
                    wait.until(ExpectedConditions.stalenessOf(anchor));
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(TABLE_ROW_SEL)));
                    Thread.sleep(PAGE_DELAY_MS);
                    log.info("[투수] 전체 필터 선택 완료 (이전: {})", current);
                    return;
                } catch (Exception ignored) {}
            }

            // 라디오 버튼 방식 fallback: "전체" 라벨 클릭
            for (WebElement lbl : driver.findElements(By.tagName("label"))) {
                if ("전체".equals(lbl.getText().trim())) {
                    WebElement anchor = driver.findElement(By.cssSelector(TABLE_ROW_SEL));
                    lbl.click();
                    wait.until(ExpectedConditions.stalenessOf(anchor));
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(TABLE_ROW_SEL)));
                    Thread.sleep(PAGE_DELAY_MS);
                    log.info("[투수] 전체 필터 선택 완료 (label)");
                    return;
                }
            }

            log.warn("[투수] 전체 필터 미발견 → 기본값으로 진행");
        } catch (Exception e) {
            log.warn("[투수] 전체 필터 선택 실패 → 기본값으로 진행: {}", e.getMessage());
        }
    }

    private String extractName(Element col) {
        Element link = col.selectFirst("a");
        String text = (link != null ? link.text() : col.text()).trim();
        int paren = text.indexOf('(');
        return paren > 0 ? text.substring(0, paren).trim() : text;
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
