package com.kbo.stats.mapper;

import com.kbo.stats.domain.PitcherStats;
import com.kbo.stats.dto.LeagueTotalsDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PitcherStatsMapper {

    // 단건 조회
    PitcherStats findByPlayerId(Long playerId);

    // INSERT ... ON DUPLICATE KEY UPDATE
    void upsert(PitcherStats stats);

    // 전체 삭제 (크롤링 재실행 시 초기화)
    void deleteAll();

    // 전체 조회 (리그 평균 계산용)
    List<PitcherStats> findAll();

    // 리그 집계 합계 (K/9·BB/9·HR/9·K/BB 가중 평균 계산용)
    LeagueTotalsDto findLeagueTotals();
}
