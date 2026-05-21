package com.kbo.stats.scheduler;

import com.kbo.stats.dto.BoxScoreCrawlSummary;
import com.kbo.stats.service.BoxScoreCrawler;
import com.kbo.stats.service.CrawlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlingScheduler {

    private final CrawlingService crawlingService;
    private final BoxScoreCrawler boxScoreCrawler;

    @Scheduled(cron = "0 0 23 * * *")
    public void scheduledCrawl() {
        log.info(">>> 자동 크롤링 스케줄 시작 (매일 23:00)");
        try {
            crawlingService.crawlAll();
        } catch (Exception e) {
            log.error("자동 크롤링 중 오류 발생: {}", e.getMessage(), e);
        }
        log.info(">>> 자동 크롤링 스케줄 완료");
    }

    @Scheduled(cron = "0 50 23 * * *")
    public void scheduledBoxScoreCrawl() {
        log.info(">>> 박스스코어 자동 수집 스케줄 시작 (매일 23:50)");
        try {
            LocalDate today = LocalDate.now();
            BoxScoreCrawlSummary summary = boxScoreCrawler.crawlByDate(today);
            log.info(">>> 박스스코어 자동 수집 완료: date={} total={} success={} failed={}",
                    today,
                    summary.getTotalCount(),
                    summary.getSuccessCount(),
                    summary.getFailedCount());
        } catch (Exception e) {
            log.error("박스스코어 자동 수집 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
