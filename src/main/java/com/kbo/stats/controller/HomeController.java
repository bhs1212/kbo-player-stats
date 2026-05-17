package com.kbo.stats.controller;

import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.GameStatus;
import com.kbo.stats.domain.UserAccount;
import com.kbo.stats.service.DashboardService;
import com.kbo.stats.service.GameService;
import com.kbo.stats.service.UserAccountService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Hidden
@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final DashboardService dashboardService;
    private final GameService gameService;
    private final UserAccountService userAccountService;

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal UserDetails principal, Model model) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate yesterday = today.minusDays(1);
        log.info("대시보드 조회: today={}, yesterday={}", today, yesterday);

        // 오늘 경기 (SCHEDULED 포함 전체)
        List<Game> todayGames = gameService.getGamesByDate(today);
        log.info("오늘 경기 수: {}", todayGames.size());
        model.addAttribute("todayGames", todayGames);

        // 어제 경기 - FINISHED 상태만 결과로 표시
        List<Game> allYesterdayGames = gameService.getGamesByDate(yesterday);
        log.info("====== [어제 경기 진단] ======");
        log.info("[진단] yesterday={}, allYesterdayGames={}건", yesterday, allYesterdayGames.size());
        if (allYesterdayGames.isEmpty()) {
            log.warn("[진단] findByDate({}) 결과 0건 - DB에 해당 날짜 경기 없거나 쿼리 파라미터 불일치", yesterday);
        }
        allYesterdayGames.forEach(g -> log.info(
                "  [진단] id={}, {} vs {}, status={}, statusClass={}, date={}",
                g.getId(), g.getAwayTeam(), g.getHomeTeam(), g.getStatus(),
                g.getStatus() == null ? "NULL!" : g.getStatus().getClass().getSimpleName(),
                g.getGameDate()));

        List<Game> yesterdayFinished = allYesterdayGames.stream()
                .filter(g -> GameStatus.FINISHED == g.getStatus())
                .toList();
        log.info("[진단] FINISHED 필터 후: {}건 (전체 {}건 중 {}건 제외)",
                yesterdayFinished.size(), allYesterdayGames.size(),
                allYesterdayGames.size() - yesterdayFinished.size());
        log.info("============================");
        model.addAttribute("yesterdayGames", yesterdayFinished);

        // 시즌 랭킹 TOP5 (캐싱됨)
        model.addAttribute("battingTop5", dashboardService.getSeasonRanking("batting", 5));
        model.addAttribute("homeRunTop5", dashboardService.getSeasonRanking("homerun", 5));
        model.addAttribute("eraTop5", dashboardService.getSeasonRanking("era", 5));
        model.addAttribute("winsTop5", dashboardService.getSeasonRanking("wins", 5));

        // 팀 순위 (캐싱됨)
        model.addAttribute("teamStandings", dashboardService.getTeamStandings());
        model.addAttribute("today", today);

        // 로그인 사용자 전용: 응원팀 맞춤 데이터
        if (principal != null) {
            try {
                UserAccount user = userAccountService.findByUsername(principal.getUsername());
                String favoriteTeam = user.getFavoriteTeam();

                if (favoriteTeam != null && !favoriteTeam.isBlank()) {
                    model.addAttribute("favoriteTeam", favoriteTeam);

                    // 응원팀 어제 경기 (FINISHED, 팀 필터링 - 컨트롤러에서 처리)
                    List<Game> teamYesterdayGames = yesterdayFinished.stream()
                            .filter(g -> favoriteTeam.equals(g.getAwayTeam())
                                      || favoriteTeam.equals(g.getHomeTeam()))
                            .toList();
                    log.info("[진단] favoriteTeam='{}', yesterdayFinished={}건, teamYesterdayGames={}건",
                            favoriteTeam, yesterdayFinished.size(), teamYesterdayGames.size());
                    if (teamYesterdayGames.isEmpty() && !yesterdayFinished.isEmpty()) {
                        yesterdayFinished.forEach(g -> log.warn(
                                "[진단] 팀 필터 제외: id={}, awayTeam='{}', homeTeam='{}' (favoriteTeam='{}')",
                                g.getId(), g.getAwayTeam(), g.getHomeTeam(), favoriteTeam));
                    }
                    teamYesterdayGames.forEach(g -> log.info("  [진단] 팀 경기 일치: id={}, {} {}:{} {}",
                            g.getId(), g.getAwayTeam(), g.getAwayScore(), g.getHomeScore(), g.getHomeTeam()));
                    model.addAttribute("teamYesterdayGames", teamYesterdayGames);

                    model.addAttribute("teamStanding",
                            dashboardService.getTeamStanding(favoriteTeam).orElse(null));
                    model.addAttribute("recentTeamGames",
                            gameService.getRecentByTeam(favoriteTeam, 5));
                    model.addAttribute("nextTeamGame",
                            dashboardService.getNextTeamGame(favoriteTeam, today).orElse(null));
                    model.addAttribute("teamLeaders",
                            dashboardService.getTeamLeaders(favoriteTeam));
                }
            } catch (Exception e) {
                log.warn("응원팀 데이터 로드 실패 - user={}, error={}", principal.getUsername(), e.getMessage());
            }
        }

        return "home/dashboard";
    }
}
