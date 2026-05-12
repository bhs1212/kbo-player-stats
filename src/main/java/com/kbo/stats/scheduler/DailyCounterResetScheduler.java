package com.kbo.stats.scheduler;

import com.kbo.stats.config.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyCounterResetScheduler {

    private final RateLimitInterceptor rateLimitInterceptor;

    // 매일 자정에 일일 챗봇 요청 카운터 초기화
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyCounter() {
        rateLimitInterceptor.resetDailyCount();
        log.info(">>> 일일 챗봇 사용량 카운터 리셋");
    }
}
