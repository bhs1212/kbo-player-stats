package com.kbo.stats;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@MapperScan("com.kbo.stats.mapper")
public class KboPlayerStatsApplication {
    public static void main(String[] args) {
        SpringApplication.run(KboPlayerStatsApplication.class, args);
    }
}
