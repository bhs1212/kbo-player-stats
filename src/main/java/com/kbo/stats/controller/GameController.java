package com.kbo.stats.controller;

import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.GameInningScore;
import com.kbo.stats.domain.UserAccount;
import com.kbo.stats.dto.CalendarEventDto;
import com.kbo.stats.dto.GameDetailDto;
import com.kbo.stats.service.GameCrawler;
import com.kbo.stats.service.GameDetailService;
import com.kbo.stats.service.GameService;
import com.kbo.stats.service.UserAccountService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Hidden
@Controller
@RequestMapping("/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final GameCrawler gameCrawler;
    private final UserAccountService userAccountService;
    private final GameDetailService gameDetailService;

    private static final List<String> KBO_TEAMS = List.of(
            "두산", "LG", "SSG", "키움", "삼성", "KT", "롯데", "한화", "NC", "KIA");
    private static final int SEASON_START_MONTH = 3;
    private static final int SEASON_END_MONTH = 11;

    /**
     * 경기 일정 목록
     * - month: 선택 월 (YYYY-MM, 없으면 현재 월), 시즌 범위 3~11월
     * - team: 팀 필터 (없으면 전체 또는 응원팀 자동 선택)
     */
    @GetMapping
    public String list(@RequestParam(required = false) String month,
            @RequestParam(required = false) String team,
            @AuthenticationPrincipal UserDetails principal,
            Model model) {

        YearMonth ym = parseYearMonth(month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        // 로그인 사용자의 응원팀 자동 선택 (team 파라미터 없을 때만)
        String resolvedTeam = team;
        // "ALL" 명시적으로 들어오면 전체 조회 (응원팀 fallback 안 함)
        if ("ALL".equals(resolvedTeam)) {
            resolvedTeam = null;
        } else if ((resolvedTeam == null || resolvedTeam.isBlank()) && principal != null) {
            try {
                UserAccount user = userAccountService.findByUsername(principal.getUsername());
                resolvedTeam = user.getFavoriteTeam();
            } catch (Exception ignored) {
            }
        }

        Map<LocalDate, List<Game>> gamesByDate = gameService.getGamesGroupedByDate(resolvedTeam, startDate, endDate);

        // 날짜 레이블 (경기가 있는 날짜만)
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("M월 d일");
        Map<LocalDate, String> dateLabels = new LinkedHashMap<>();
        for (LocalDate d : gamesByDate.keySet()) {
            String dayName = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
            dateLabels.put(d, d.format(dateFmt) + " (" + dayName + ")");
        }

        // 월 드롭다운 옵션: [value, label] 쌍 리스트 (Thymeleaf Map 룩업 대신 배열 인덱스 사용)
        int year = ym.getYear();
        List<String[]> monthOptions = new ArrayList<>();
        for (int m = SEASON_START_MONTH; m <= SEASON_END_MONTH; m++) {
            monthOptions.add(new String[] {
                    YearMonth.of(year, m).toString(),
                    year + "년 " + m + "월"
            });
        }

        String selectedMonth = ym.toString();
        String prevMonth = ym.getMonthValue() > SEASON_START_MONTH ? ym.minusMonths(1).toString() : null;
        String nextMonth = ym.getMonthValue() < SEASON_END_MONTH ? ym.plusMonths(1).toString() : null;

        model.addAttribute("gamesByDate", gamesByDate);
        model.addAttribute("dateLabels", dateLabels);
        model.addAttribute("teams", KBO_TEAMS);
        model.addAttribute("selectedTeam", resolvedTeam != null ? resolvedTeam : "ALL");
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedMonthDisplay", year + "년 " + ym.getMonthValue() + "월");
        model.addAttribute("monthOptions", monthOptions);
        model.addAttribute("prevMonth", prevMonth);
        model.addAttribute("nextMonth", nextMonth);
        model.addAttribute("isCurrentMonth", ym.equals(YearMonth.now()));
        model.addAttribute("isEmpty", gamesByDate.isEmpty());

        return "game/list";
    }

    /** FullCalendar JSON 이벤트 API */
    @GetMapping("/api/events")
    @ResponseBody
    public List<CalendarEventDto> getEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String team) {
        LocalDate s = start != null ? start : LocalDate.now().withDayOfMonth(1);
        LocalDate e = end != null ? end : s.plusMonths(1).minusDays(1);
        return gameService.findEventsForCalendar(s, e, team);
    }

    private static final Map<String, String> TEAM_HEX = Map.ofEntries(
        Map.entry("KIA",  "#EA002C"), Map.entry("KT",  "#000000"),
        Map.entry("LG",   "#C30452"), Map.entry("NC",  "#1D5288"),
        Map.entry("SSG",  "#CE0E2D"), Map.entry("두산", "#131230"),
        Map.entry("롯데", "#002955"), Map.entry("삼성", "#074CA1"),
        Map.entry("키움", "#570514"), Map.entry("한화", "#FF6600")
    );

    /** 경기 상세 */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        GameDetailDto detail;
        try {
            detail = gameDetailService.findGameDetail(id);
        } catch (IllegalArgumentException e) {
            return "error/404";
        }
        model.addAttribute("detail", detail);

        if (detail.isHasBoxScore()) {
            // null score 이닝(연장 미사용 placeholder)은 제외하고 실제 최대 이닝 산출
            int maxInning = detail.getInnings().stream()
                .filter(i -> i.getScore() != null)
                .mapToInt(GameInningScore::getInning).max().orElse(9);
            maxInning = Math.max(maxInning, 9);

            model.addAttribute("maxInning", maxInning);
            model.addAttribute("awayInningScores", buildInningScores(detail.getInnings(), "AWAY", maxInning));
            model.addAttribute("homeInningScores", buildInningScores(detail.getInnings(), "HOME", maxInning));
        }

        model.addAttribute("awayTeamColor", TEAM_HEX.getOrDefault(detail.getGame().getAwayTeam(), "#666666"));
        model.addAttribute("homeTeamColor", TEAM_HEX.getOrDefault(detail.getGame().getHomeTeam(), "#666666"));

        return "game/detail";
    }

    private List<Integer> buildInningScores(List<GameInningScore> innings, String side, int maxInning) {
        // Collectors.toMap은 null 값 불허 → HashMap으로 직접 수집
        Map<Integer, Integer> map = new HashMap<>();
        innings.stream()
            .filter(i -> side.equals(i.getTeamSide()))
            .forEach(i -> map.put(i.getInning(), i.getScore()));
        return IntStream.rangeClosed(1, maxInning)
            .mapToObj(map::get)
            .collect(Collectors.toList());
    }

    /** ADMIN 전용: 시즌 전체(3~11월) 크롤링 */
    @PostMapping("/crawl")
    public String triggerCrawl(RedirectAttributes redirectAttributes) {
        int count = gameCrawler.crawlAllGames();
        redirectAttributes.addFlashAttribute(
                count > 0 ? "successMessage" : "errorMessage",
                count > 0 ? "시즌 전체 " + count + "건의 경기 데이터를 갱신했습니다." : "크롤링 결과가 없습니다. 로그를 확인하세요.");
        return "redirect:/games";
    }

    /** ADMIN 전용: 시즌 전체(3~11월) 크롤링 */
    @PostMapping("/crawl-all")
    public String triggerCrawlAll(RedirectAttributes redirectAttributes) {
        int count = gameCrawler.crawlAllGames();
        redirectAttributes.addFlashAttribute(
                count > 0 ? "successMessage" : "errorMessage",
                count > 0 ? "시즌 전체 " + count + "건의 경기 데이터를 갱신했습니다." : "크롤링 결과가 없습니다. 로그를 확인하세요.");
        return "redirect:/games";
    }

    private YearMonth parseYearMonth(String monthStr) {
        if (monthStr != null && !monthStr.isBlank()) {
            try {
                YearMonth ym = YearMonth.parse(monthStr);
                int m = ym.getMonthValue();
                if (m < SEASON_START_MONTH)
                    return YearMonth.of(ym.getYear(), SEASON_START_MONTH);
                if (m > SEASON_END_MONTH)
                    return YearMonth.of(ym.getYear(), SEASON_END_MONTH);
                return ym;
            } catch (Exception ignored) {
            }
        }
        YearMonth now = YearMonth.now();
        int m = now.getMonthValue();
        if (m < SEASON_START_MONTH)
            return YearMonth.of(now.getYear(), SEASON_START_MONTH);
        if (m > SEASON_END_MONTH)
            return YearMonth.of(now.getYear(), SEASON_END_MONTH);
        return now;
    }
}
