package com.kbo.stats.controller.api;

import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.*;
import com.kbo.stats.service.PlayerService;
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
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerApiController {

    private final PlayerService playerService;

    /** 선수 목록 검색 (GET /api/v1/players) */
    @GetMapping
    public ApiResponse<PageDto<Player>> list(PlayerSearchDto searchDto) {
        return ApiResponse.ok(playerService.search(searchDto));
    }

    /** 선수 단건 조회 (GET /api/v1/players/{id}) */
    @GetMapping("/{id}")
    public ApiResponse<Player> get(@PathVariable Long id) {
        return ApiResponse.ok(playerService.findById(id));
    }

    /** 선수 등록 (POST /api/v1/players) */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody PlayerFormDto form) {
        Long id = playerService.save(form);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("선수가 등록되었습니다.", id));
    }

    /** 선수 수정 (PUT /api/v1/players/{id}) */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody PlayerFormDto form) {
        playerService.update(id, form);
        return ApiResponse.ok("선수 정보가 수정되었습니다.", null);
    }

    /** 선수 삭제 (DELETE /api/v1/players/{id}) */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        playerService.delete(id);
        return ApiResponse.ok("선수가 삭제되었습니다.", null);
    }

    /** 타율 랭킹 (GET /api/v1/players/ranking/batting) */
    @GetMapping("/ranking/batting")
    public ApiResponse<List<Player>> battingRanking(
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(playerService.getBattingRanking(limit));
    }

    /** 홈런 랭킹 (GET /api/v1/players/ranking/homerun) */
    @GetMapping("/ranking/homerun")
    public ApiResponse<List<Player>> homeRunRanking(
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(playerService.getHomeRunRanking(limit));
    }

    /** 방어율 랭킹 (GET /api/v1/players/ranking/era) */
    @GetMapping("/ranking/era")
    public ApiResponse<List<Player>> eraRanking(
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(playerService.getEraRanking(limit));
    }

    /** 승리 랭킹 (GET /api/v1/players/ranking/wins) */
    @GetMapping("/ranking/wins")
    public ApiResponse<List<Player>> winsRanking(
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(playerService.getWinsRanking(limit));
    }

    /** 팀별 통계 (GET /api/v1/players/stats/team) */
    @GetMapping("/stats/team")
    public ApiResponse<List<TeamStatDto>> teamStats() {
        return ApiResponse.ok(playerService.getTeamStats());
    }

    /** 팀 목록 (GET /api/v1/players/teams) */
    @GetMapping("/teams")
    public ApiResponse<List<String>> teams() {
        return ApiResponse.ok(playerService.findAllTeams());
    }
}
