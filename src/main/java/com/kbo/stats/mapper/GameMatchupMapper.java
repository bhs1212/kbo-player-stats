package com.kbo.stats.mapper;

import com.kbo.stats.domain.GameMatchup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GameMatchupMapper {
    void insertBatch(@Param("matchups") List<GameMatchup> matchups);
    void deleteByGameId(Long gameId);
    List<GameMatchup> findByGameId(Long gameId);
}
