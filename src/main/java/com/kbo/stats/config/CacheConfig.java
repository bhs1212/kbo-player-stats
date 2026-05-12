package com.kbo.stats.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

// 챗봇 응답 캐싱 설정: 동일 질문 반복 시 LLM 호출 없이 즉시 반환
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("chatAnswers");
        manager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS) // 1시간 후 자동 만료
                .maximumSize(200)                    // 최대 200개 항목 보관
        );
        return manager;
    }
}
