package com.kbo.stats.controller.api;

import com.kbo.stats.domain.Game;
import com.kbo.stats.dto.ApiResponse;
import com.kbo.stats.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "경기 API", description = "KBO 경기 일정 및 결과 조회 REST API")
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameApiController {

    private final GameService gameService;

    @Operation(summary = "기간별 경기 조회", description = "from~to 기간의 경기 목록을 반환합니다.")
    @GetMapping
    public ApiResponse<List<Game>> list(
            @Parameter(description = "시작일 (yyyy-MM-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일 (yyyy-MM-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "팀명 필터 (선택)") @RequestParam(required = false) String team) {

        List<Game> games = (team == null || team.isBlank())
                ? gameService.getGamesInRange(from, to)
                : gameService.getGamesByTeam(team, from, to);
        return ApiResponse.ok(games);
    }

    @Operation(summary = "경기 단건 조회", description = "ID로 경기 상세 정보를 반환합니다.")
    @GetMapping("/{id}")
    public ApiResponse<Game> get(
            @Parameter(description = "경기 ID") @PathVariable Long id) {
        return ApiResponse.ok(gameService.getGameById(id));
    }
}
