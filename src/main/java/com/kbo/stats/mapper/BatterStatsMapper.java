package com.kbo.stats.mapper;

import com.kbo.stats.domain.BatterStats;
import com.kbo.stats.dto.BatterLeagueTotalsDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BatterStatsMapper {

    // 단건 조회
    BatterStats findByPlayerId(Long playerId);

    // INSERT ... ON DUPLICATE KEY UPDATE
    void upsert(BatterStats stats);

    // 전체 삭제 (크롤링 재실행 시 초기화)
    void deleteAll();

    // 전체 조회 (리그 평균 계산용)
    List<BatterStats> findAll();

    // 리그 집계 합계 (ISO·BABIP·wOBA·BB/K 가중 평균 계산용, player JOIN 포함)
    BatterLeagueTotalsDto findLeagueTotals();
}
