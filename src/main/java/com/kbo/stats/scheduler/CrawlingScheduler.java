package com.kbo.stats.scheduler;

import com.kbo.stats.service.CrawlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlingScheduler {

    private final CrawlingService crawlingService;

    /**
     * 매일 밤 11시에 자동 실행
     * cron = "초 분 시 일 월 요일"
     */
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
}
