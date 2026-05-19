package com.kbo.stats.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // 챗봇 응답 캐시: 동일 질문 반복 시 LLM 호출 없이 즉시 반환 (1시간)
        manager.registerCustomCache("chatAnswers",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(200)
                        .<Object, Object>build());

        // 팀 순위 캐시: 경기 결과 집계 (5분)
        manager.registerCustomCache("teamStandings",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(50)
                        .<Object, Object>build());

        // 시즌 랭킹 캐시: 타율/홈런/방어율/승리 상위 선수 (5분)
        manager.registerCustomCache("seasonRankings",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(50)
                        .<Object, Object>build());

        // 리그 분포 캐시: 백분위 계산용 전체 리스트 (5분)
        manager.registerCustomCache("league_distributions",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(20)
                        .<Object, Object>build());

        return manager;
    }
}
