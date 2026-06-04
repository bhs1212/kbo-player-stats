package com.kbo.stats.controller.api;

import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.*;
import com.kbo.stats.service.PlayerService;
import com.kbo.stats.service.PlayerVsTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "선수 API", description = "선수 조회 REST API")
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerApiController {

    private final PlayerService       playerService;
    private final PlayerVsTeamService playerVsTeamService;

    @Operation(summary = "선수 목록 검색", description = "이름, 팀, 포지션, 선수 유형으로 필터링하여 페이지네이션된 선수 목록을 반환합니다.")
    @GetMapping
    public ApiResponse<PageDto<Player>> list(PlayerSearchDto searchDto) {
        return ApiResponse.ok(playerService.search(searchDto));
    }

    @Operation(summary = "선수 단건 조회", description = "ID로 선수 한 명의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ApiResponse<Player> get(
            @Parameter(description = "선수 ID") @PathVariable Long id) {
        return ApiResponse.ok(playerService.findById(id));
    }

    @Operation(summary = "타율 랭킹", description = "타율 기준 상위 타자 랭킹을 반환합니다.")
    @GetMapping("/ranking/batting")
    public ApiResponse<List<Player>> battingRanking(
            @Parameter(description = "조회할 선수 수 (기본값: 30)") @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(playerService.getBattingRanking(limit));
    }

    @Operation(summary = "홈런 랭킹", description = "홈런 기준 상위 타자 랭킹을 반환합니다.")
    @GetMapping("/ranking/homerun")
    public ApiResponse<List<Player>> homeRunRanking(
            @Parameter(description = "조회할 선수 수 (기본값: 30)") @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(playerService.getHomeRunRanking(limit));
    }

    @Operation(summary = "방어율 랭킹", description = "ERA 기준 상위 투수 랭킹을 반환합니다.")
    @GetMapping("/ranking/era")
    public ApiResponse<List<Player>> eraRanking(
            @Parameter(description = "조회할 선수 수 (기본값: 30)") @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(playerService.getEraRanking(limit));
    }

    @Operation(summary = "승리 랭킹", description = "승리 수 기준 상위 투수 랭킹을 반환합니다.")
    @GetMapping("/ranking/wins")
    public ApiResponse<List<Player>> winsRanking(
            @Parameter(description = "조회할 선수 수 (기본값: 30)") @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(playerService.getWinsRanking(limit));
    }

    @Operation(summary = "팀별 통계", description = "각 팀의 집계 통계를 반환합니다.")
    @GetMapping("/stats/team")
    public ApiResponse<List<TeamStatDto>> teamStats() {
        return ApiResponse.ok(playerService.getTeamStats());
    }

    @Operation(summary = "팀 목록", description = "DB에 존재하는 모든 팀명 목록을 반환합니다.")
    @GetMapping("/teams")
    public ApiResponse<List<String>> teams() {
        return ApiResponse.ok(playerService.findAllTeams());
    }

    @Operation(summary = "상대 팀별 전적", description = "선수 ID로 상대 팀별 집계 전적을 반환합니다. 타자는 타율 내림차순, 투수는 ERA 오름차순 정렬.")
    @GetMapping("/{id}/vs-team")
    public ResponseEntity<List<PlayerVsTeamDto>> getVsTeam(
            @Parameter(description = "선수 ID") @PathVariable Long id) {
        return ResponseEntity.ok(playerVsTeamService.findByPlayerId(id));
    }
}
