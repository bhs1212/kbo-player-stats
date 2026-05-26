package com.kbo.stats.controller;

import com.kbo.stats.service.BoxScoreCrossValidationService;
import com.kbo.stats.service.CrawlingService;
import com.kbo.stats.service.MatchupRebuildService;
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

    private final CrawlingService                crawlingService;
    private final BoxScoreCrossValidationService boxScoreCrossValidationService;
    private final MatchupRebuildService          matchupRebuildService;

    /** 수동 크롤링 실행 */
    @PostMapping("/run")
    public String runCrawling(RedirectAttributes redirectAttrs) {
        try {
            crawlingService.crawlAll();
            redirectAttrs.addFlashAttribute("successMessage", "크롤링이 완료되었습니다.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "크롤링 실패: " + e.getMessage());
        }
        return "redirect:/players";
    }

    /** 박스스코어 ↔ 시즌 통계 교차 검증 수동 트리거 */
    @ResponseBody
    @PostMapping("/cross-validation")
    public ResponseEntity<String> runCrossValidation() {
        boxScoreCrossValidationService.runCrossValidation();
        return ResponseEntity.ok("교차 검증 완료. stat_validation_log에서 BATTING_AVG_BOXSCORE / ERA_BOXSCORE / WHIP_BOXSCORE 조회.");
    }

    /** 특정 경기의 타자-투수 매치업 재구성 (ADMIN 전용) */
    @ResponseBody
    @PostMapping("/matchup-rebuild/{gameId}")
    public ResponseEntity<Map<String, Object>> rebuildMatchup(@PathVariable Long gameId) {
        int count = matchupRebuildService.rebuildOne(gameId);
        return ResponseEntity.ok(Map.of("gameId", gameId, "matchups", count));
    }

    /** 전체 FINISHED 경기 매치업 재구성 (ADMIN 전용) */
    @ResponseBody
    @PostMapping("/matchup-rebuild-all")
    public ResponseEntity<Map<String, Object>> rebuildAllMatchups() {
        Map<String, Object> result = matchupRebuildService.rebuildAll();
        return ResponseEntity.ok(result);
    }
}
