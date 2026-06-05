package com.kbo.stats.controller;

import com.kbo.stats.dto.BoxScoreCollectResult;
import com.kbo.stats.dto.BoxScoreCrawlSummary;
import com.kbo.stats.service.BoxScoreCollectService;
import com.kbo.stats.service.BoxScoreCrawler;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Hidden
@RestController
@RequestMapping("/crawling")
@RequiredArgsConstructor
public class BoxScoreCrawlController {

    private final BoxScoreCrawler        crawler;
    private final BoxScoreCollectService collectService;

    /**
     * 박스스코어 수집 트리거 (ADMIN 전용).
     * - {@code ?gameId=N}    단일 경기 수집 (테스트·재수집)
     * - {@code ?date=YYYYMMDD} 해당 날짜 전체 수집
     * 둘 다 없거나 둘 다 있으면 400.
     */
    @PostMapping("/boxscore")
    public ResponseEntity<?> trigger(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long   gameId) {

        if (date == null && gameId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "date 또는 gameId 파라미터가 필요합니다."));
        }
        if (date != null && gameId != null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "date, gameId 중 하나만 지정하세요."));
        }

        if (gameId != null) {
            BoxScoreCollectResult result = collectService.collectOne(gameId);
            return ResponseEntity.ok(result);
        }

        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
        BoxScoreCrawlSummary summary = crawler.crawlByDate(localDate);
        return ResponseEntity.ok(summary);
    }

    /** 날짜 범위 박스스코어 일괄 재수집 (ADMIN 전용). ?start=YYYYMMDD&end=YYYYMMDD */
    @PostMapping("/boxscore-range")
    public ResponseEntity<BoxScoreCrawlSummary> triggerRange(
            @RequestParam String start,
            @RequestParam String end) {
        LocalDate startDate = LocalDate.parse(start, DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDate endDate   = LocalDate.parse(end,   DateTimeFormatter.ofPattern("yyyyMMdd"));
        BoxScoreCrawlSummary summary = crawler.crawlRange(startDate, endDate);
        return ResponseEntity.ok(summary);
    }

}
