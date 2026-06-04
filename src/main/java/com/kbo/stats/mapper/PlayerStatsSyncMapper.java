package com.kbo.stats.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayerStatsSyncMapper {
    int syncBatterStats();
    int syncPitcherStats();
    int ensureBatterStatsRows();
    int ensurePitcherStatsRows();
    int syncBatterSabermetrics();
    int syncPitcherSabermetrics();
}
