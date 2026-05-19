package com.kbo.stats.mapper;

import com.kbo.stats.domain.GameInningScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GameInningScoreMapper {
    void insert(GameInningScore e);
    int deleteByGameId(@Param("gameId") Long gameId);
    List<GameInningScore> findByGameId(@Param("gameId") Long gameId);
}
