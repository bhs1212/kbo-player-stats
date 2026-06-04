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

        // stub 선수도 sabermetrics 갱신 받도록 row 보장
        syncMapper.ensureBatterStatsRows();
        syncMapper.ensurePitcherStatsRows();

        int batterSab      = syncMapper.syncBatterSabermetrics();
        int pitcherSab     = syncMapper.syncPitcherSabermetrics();

        long durationMs = System.currentTimeMillis() - start;
        log.info("[PlayerStatsSync] 완료 batters={} pitchers={} batterSab={} pitcherSab={} durationMs={}",
                batterUpdated, pitcherUpdated, batterSab, pitcherSab, durationMs);

        return Map.of(
                "batterUpdated",              batterUpdated,
                "pitcherUpdated",             pitcherUpdated,
                "batterSabermetricsUpdated",  batterSab,
                "pitcherSabermetricsUpdated", pitcherSab,
                "durationMs",                 durationMs
        );
    }
}
