package com.kbo.stats.controller;

import com.kbo.stats.dto.BoxScoreCrawlSummary;
import com.kbo.stats.service.BoxScoreCrawler;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Hidden
@Controller
@RequestMapping("/crawling")
@RequiredArgsConstructor
public class CrawlingController {

    private final BoxScoreCrawler                boxScoreCrawler;
    private final BoxScoreCrossValidationService boxScoreCrossValidationService;
    private final MatchupRebuildService          matchupRebuildService;
    private final PlayerStatsSyncService         playerStatsSyncService;

    @PostMapping("/boxscore-refresh")
    public String refreshBoxScore(RedirectAttributes redirectAttrs) {
        try {
            BoxScoreCrawlSummary summary = boxScoreCrawler.crawlMissing();
            if (summary.getTotalCount() == 0) {
                redirectAttrs.addFlashAttribute("infoMessage", "갱신할 박스스코어 없음 (모두 최신)");
            } else {
                redirectAttrs.addFlashAttribute("successMessage",
                    String.format("박스스코어 갱신 완료: 누락 %d건 중 성공 %d건, 실패 %d건 (%.1f초)",
                        summary.getTotalCount(), summary.getSuccessCount(),
                        summary.getFailedCount(), summary.getDurationMs() / 1000.0));
            }
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "박스스코어 갱신 실패: " + e.getMessage());
        }
        return "redirect:/games";
    }

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
