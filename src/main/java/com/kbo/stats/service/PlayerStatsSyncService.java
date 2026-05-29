package com.kbo.stats.service;

import com.kbo.stats.mapper.PlayerStatsSyncMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerStatsSyncService {

    private final PlayerStatsSyncMapper syncMapper;

    @Transactional
    public Map<String, Object> syncAll() {
        log.info("[PlayerStatsSync] 시작");
        long start = System.currentTimeMillis();

        int batterUpdated  = syncMapper.syncBatterStats();
        int pitcherUpdated = syncMapper.syncPitcherStats();

        long durationMs = System.currentTimeMillis() - start;
        log.info("[PlayerStatsSync] 완료 batters={} pitchers={} durationMs={}",
                batterUpdated, pitcherUpdated, durationMs);

        return Map.of(
                "batterUpdated",  batterUpdated,
                "pitcherUpdated", pitcherUpdated,
                "durationMs",     durationMs
        );
    }
}
