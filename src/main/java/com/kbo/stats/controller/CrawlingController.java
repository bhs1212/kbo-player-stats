package com.kbo.stats.controller;

import com.kbo.stats.service.BoxScoreCrossValidationService;
import com.kbo.stats.service.MatchupRebuildService;
import com.kbo.stats.service.PlayerStatsSyncService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Hidden
@Controller
@RequestMapping("/crawling")
@RequiredArgsConstructor
public class CrawlingController {

    private final BoxScoreCrossValidationService boxScoreCrossValidationService;
    private final MatchupRebuildService          matchupRebuildService;
    private final PlayerStatsSyncService         playerStatsSyncService;

    @ResponseBody
    @PostMapping("/cross-validation")
    public ResponseEntity<String> runCrossValidation() {
        boxScoreCrossValidationService.runCrossValidation();
        return ResponseEntity.ok("교차 검증 완료. stat_validation_log에서 BATTING_AVG_BOXSCORE / ERA_BOXSCORE / WHIP_BOXSCORE 조회.");
    }

    @ResponseBody
    @PostMapping("/matchup-rebuild/{gameId}")
    public ResponseEntity<Map<String, Object>> rebuildMatchup(@PathVariable Long gameId) {
        int count = matchupRebuildService.rebuildOne(gameId);
        return ResponseEntity.ok(Map.of("gameId", gameId, "matchups", count));
    }

    @ResponseBody
    @PostMapping("/matchup-rebuild-all")
    public ResponseEntity<Map<String, Object>> rebuildAllMatchups() {
        Map<String, Object> result = matchupRebuildService.rebuildAll();
        return ResponseEntity.ok(result);
    }

    @ResponseBody
    @PostMapping("/sync-player-stats")
    public ResponseEntity<Map<String, Object>> syncPlayerStats() {
        Map<String, Object> result = playerStatsSyncService.syncAll();
        return ResponseEntity.ok(result);
    }
}
