package com.kbo.stats.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    // IP별 버킷 저장소
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    // 오늘 총 요청 수 카운터
    private final AtomicLong dailyCount = new AtomicLong(0);

    @Value("${chat.rate-limit.per-minute-per-ip:10}")
    private int perMinutePerIp;

    @Value("${chat.rate-limit.daily-total:500}")
    private long dailyTotal;

    // IP별 분당 허용 토큰을 가진 새 버킷 생성
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(
            perMinutePerIp,
            Refill.greedy(perMinutePerIp, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        // 일일 총량 초과 확인
        if (dailyCount.get() >= dailyTotal) {
            log.warn("일일 챗봇 사용량 초과 - dailyCount={}", dailyCount.get());
            sendJson(response, "{\"success\":false,\"message\":\"오늘의 챗봇 사용량이 초과되었습니다. 내일 다시 이용해주세요.\",\"data\":null}");
            return false;
        }

        // 클라이언트 IP 추출 (프록시 환경 대응)
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            // X-Forwarded-For에 여러 IP가 있으면 첫 번째가 실제 클라이언트 IP
            ip = ip.split(",")[0].trim();
        }

        // IP별 버킷 조회 또는 신규 생성
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createNewBucket());

        if (!bucket.tryConsume(1)) {
            log.warn("Rate Limit 초과 - ip={}", ip);
            sendJson(response, "{\"success\":false,\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\",\"data\":null}");
            return false;
        }

        dailyCount.incrementAndGet();
        return true;
    }

    // 매일 자정 스케줄러에서 호출하여 카운터 초기화
    public void resetDailyCount() {
        dailyCount.set(0);
    }

    private void sendJson(HttpServletResponse response, String body) throws IOException {
        response.setStatus(429); // 429 Too Many Requests
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(body);
    }
}
