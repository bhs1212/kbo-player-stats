package com.kbo.stats.mapper;

import com.kbo.stats.domain.GamePitcherLog;
import com.kbo.stats.dto.PitcherBoxScoreAggregate;
import com.kbo.stats.dto.PlayerVsTeamDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GamePitcherLogMapper {
    void insert(GamePitcherLog e);
    int deleteByGameId(@Param("gameId") Long gameId);
    List<GamePitcherLog> findByGameId(@Param("gameId") Long gameId);
    List<GamePitcherLog> findAllByGameId(@Param("gameId") Long gameId);
    List<GamePitcherLog> findByGameIdAndTeamSide(@Param("gameId") Long gameId, @Param("teamSide") String teamSide);
    PitcherBoxScoreAggregate aggregateByPlayerId(@Param("playerId") Long playerId);
    List<PlayerVsTeamDto> aggregatePitcherVsTeams(@Param("playerName") String playerName);
}
