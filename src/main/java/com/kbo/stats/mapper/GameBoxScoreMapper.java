package com.kbo.stats.mapper;

import com.kbo.stats.domain.GameBoxScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GameBoxScoreMapper {
    void upsert(GameBoxScore e);
    int deleteByGameId(@Param("gameId") Long gameId);
    GameBoxScore findByGameId(@Param("gameId") Long gameId);
}
