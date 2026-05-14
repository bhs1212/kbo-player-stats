package com.kbo.stats.mapper;

import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.GameStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface GameMapper {

    /** 기간별 경기 조회 (날짜·시간 순) */
    List<Game> findByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 특정 날짜 경기 조회 */
    List<Game> findByDate(@Param("date") LocalDate date);

    /** 팀 + 기간별 경기 조회 (홈/원정 모두 포함) */
    List<Game> findByTeamAndDateRange(@Param("team") String team,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    /** 단건 조회 */
    Optional<Game> findById(@Param("id") Long id);

    /** 등록 또는 업데이트 (날짜+원정팀+홈팀 중복 시 점수·상태 업데이트) */
    void upsert(Game game);

    /** 상태별 카운트 */
    int countByStatus(@Param("status") GameStatus status);
}
