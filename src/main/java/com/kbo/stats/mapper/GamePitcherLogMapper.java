package com.kbo.stats.mapper;

import com.kbo.stats.domain.GamePitcherLog;
import com.kbo.stats.dto.PitcherBoxScoreAggregate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GamePitcherLogMapper {
    void insert(GamePitcherLog e);
    int deleteByGameId(@Param("gameId") Long gameId);
    List<GamePitcherLog> findByGameId(@Param("gameId") Long gameId);
    List<GamePitcherLog> findAllByGameId(@Param("gameId") Long gameId);
    PitcherBoxScoreAggregate aggregateByPlayerId(@Param("playerId") Long playerId);
}
