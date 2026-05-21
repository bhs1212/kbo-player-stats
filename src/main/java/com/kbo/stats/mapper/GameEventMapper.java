package com.kbo.stats.mapper;

import com.kbo.stats.domain.GameEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GameEventMapper {
    void insert(GameEvent e);
    int deleteByGameId(@Param("gameId") Long gameId);
    List<GameEvent> findByGameId(@Param("gameId") Long gameId);
    List<GameEvent> findAllByGameId(@Param("gameId") Long gameId);
}
