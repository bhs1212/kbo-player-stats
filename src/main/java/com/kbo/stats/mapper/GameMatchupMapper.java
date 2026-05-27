package com.kbo.stats.mapper;

import com.kbo.stats.domain.GameMatchup;
import com.kbo.stats.dto.MatchupRecordRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GameMatchupMapper {
    void insertBatch(@Param("matchups") List<GameMatchup> matchups);
    void deleteByGameId(Long gameId);
    List<GameMatchup> findByGameId(Long gameId);

    List<MatchupRecordRow> findByBatterAndPitcher(@Param("batterName") String batterName,
                                                   @Param("pitcherName") String pitcherName);
    List<MatchupRecordRow> findByBatter(@Param("batterName") String batterName);
    List<MatchupRecordRow> findByPitcher(@Param("pitcherName") String pitcherName);
}
