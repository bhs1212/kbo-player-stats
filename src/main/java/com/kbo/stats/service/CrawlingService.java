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
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    private static final int CURRENT_SEASON = 2026;
    private static final long TABLE_WAIT_SECONDS = 20;
    private static final long PAGE_DELAY_MS = 2_000;

    private static final String KBO_BATTER_URL =
            "https://www.koreabaseball.com/Record/Player/HitterBasic/Basic1.aspx";
    private static final String KBO_PITCHER_URL =
            "https://www.koreabaseball.com/Record/Player/PitcherBasic/Basic1.aspx";

    private static final String TABLE_ROW_SEL = "table.tData01 tbody tr";
    private static final String PAGER_LINK_SEL = "div.paging a";

    private final PlayerService playerService;

    public void crawlAll() {
        log.info("=== KBO 크롤링 시작 (시즌: {}) ===", CURRENT_SEASON);
        playerService.deleteAll();
        int batterCount  = crawlKbo(KBO_BATTER_URL,  PlayerType.BATTER,  "타자");
        int pitcherCount = crawlKbo(KBO_PITCHER_URL, PlayerType.PITCHER, "투수");
        log.info("=== KBO 크롤링 완료 | 타자: {}명, 투수: {}명 ===", batterCount, pitcherCount);
    }

    private int crawlKbo(String url, PlayerType playerType, String label) {
        List<Player> players = new ArrayList<>();
        WebDriver driver = null;
        try {
            driver = createDriver();
            log.info("[{}] 브라우저 오픈 → {}", label, url);
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TABLE_WAIT_SECONDS));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(TABLE_ROW_SEL)));
            Thread.sleep(PAGE_DELAY_MS);

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
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
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
        Thread.sleep(500);
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
        if (cols.size() < 6) return null;

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
                .build();
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
