package com.kbo.stats.mapper;

import com.kbo.stats.domain.GameBatterLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GameBatterLogMapper {
    void insert(GameBatterLog e);
    int deleteByGameId(@Param("gameId") Long gameId);
    List<GameBatterLog> findByGameId(@Param("gameId") Long gameId);
}
