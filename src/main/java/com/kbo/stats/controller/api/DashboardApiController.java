package com.kbo.stats.controller.api;

import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.ApiResponse;
import com.kbo.stats.dto.TeamLeadersDto;
import com.kbo.stats.dto.TeamStandingDto;
import com.kbo.stats.service.DashboardService;
import com.kbo.stats.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "대시보드 API", description = "메인 대시보드 데이터 조회 REST API")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardApiController {

    private final DashboardService dashboardService;
    private final GameService gameService;

    @Operation(summary = "팀 순위 조회", description = "시즌 전체 팀 순위 (승/패/무/승률) 를 반환합니다.")
    @GetMapping("/standings")
    public ApiResponse<List<TeamStandingDto>> standings() {
        return ApiResponse.ok(dashboardService.getTeamStandings());
    }

    @Operation(summary = "특정 날짜 경기 조회", description = "지정한 날짜의 경기 목록을 반환합니다. 기본값은 오늘.")
    @GetMapping("/games")
    public ApiResponse<List<Game>> games(
            @Parameter(description = "조회 날짜 (yyyy-MM-dd, 기본값: 오늘)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = (date != null) ? date : LocalDate.now();
        return ApiResponse.ok(gameService.getGamesByDate(target));
    }

    @Operation(summary = "시즌 랭킹 조회", description = "타입별 시즌 상위 선수 랭킹을 반환합니다. type: batting/homerun/era/wins")
    @GetMapping("/ranking/{type}")
    public ApiResponse<List<Player>> ranking(
            @Parameter(description = "랭킹 타입 (batting/homerun/era/wins)") @PathVariable String type,
            @Parameter(description = "조회 수 (기본값: 5)") @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(dashboardService.getSeasonRanking(type, limit));
    }

    @Operation(summary = "팀 주요 선수 조회", description = "특정 팀의 타율/홈런/방어율 1위 선수를 반환합니다.")
    @GetMapping("/team/{team}/leaders")
    public ApiResponse<TeamLeadersDto> teamLeaders(
            @Parameter(description = "팀명") @PathVariable String team) {
        return ApiResponse.ok(dashboardService.getTeamLeaders(team));
    }

    @Operation(summary = "팀 최근 경기 조회", description = "특정 팀의 최근 완료된 N경기를 반환합니다.")
    @GetMapping("/team/{team}/recent")
    public ApiResponse<List<Game>> teamRecent(
            @Parameter(description = "팀명") @PathVariable String team,
            @Parameter(description = "조회 수 (기본값: 5)") @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(gameService.getRecentByTeam(team, limit));
    }

    @Operation(summary = "팀 다음 경기 조회", description = "특정 팀의 다음 예정 경기를 반환합니다.")
    @GetMapping("/team/{team}/next")
    public ApiResponse<Game> teamNext(
            @Parameter(description = "팀명") @PathVariable String team) {
        return ApiResponse.ok(
                dashboardService.getNextTeamGame(team, LocalDate.now()).orElse(null));
    }
}
