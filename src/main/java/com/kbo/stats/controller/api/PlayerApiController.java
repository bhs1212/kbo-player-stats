package com.kbo.stats.controller.api;

import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.*;
import com.kbo.stats.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * KBO 선수 기록 REST API
 * Base URL: /api/v1/players
 */
@Tag(name = "선수 API", description = "선수 조회/등록/수정/삭제 REST API")
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerApiController {

    private final PlayerService playerService;

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

    @Operation(summary = "선수 등록", description = "새 선수를 등록합니다. 등록 성공 시 HTTP 201과 생성된 ID를 반환합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody PlayerFormDto form) {
        Long id = playerService.save(form);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("선수가 등록되었습니다.", id));
    }

    @Operation(summary = "선수 수정", description = "ID에 해당하는 선수 정보를 수정합니다.")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @Parameter(description = "선수 ID") @PathVariable Long id,
            @Valid @RequestBody PlayerFormDto form) {
        playerService.update(id, form);
        return ApiResponse.ok("선수 정보가 수정되었습니다.", null);
    }

    @Operation(summary = "선수 삭제", description = "ID에 해당하는 선수를 삭제합니다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "선수 ID") @PathVariable Long id) {
        playerService.delete(id);
        return ApiResponse.ok("선수가 삭제되었습니다.", null);
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
}
