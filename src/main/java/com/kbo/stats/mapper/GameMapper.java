package com.kbo.stats.mapper;

import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.GameStatus;
import com.kbo.stats.dto.TeamStandingDto;
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

    /** 팀 순위 집계 (FINISHED 경기 기준 승/패/무 집계) */
    List<TeamStandingDto> findTeamStandings();

    /** 특정 팀의 최근 N경기 (완료된 경기, 최신순) */
    List<Game> findRecentByTeam(@Param("team") String team, @Param("limit") int limit);

    /** 특정 팀의 다음 예정 경기 */
    Optional<Game> findNextByTeam(@Param("team") String team, @Param("afterDate") LocalDate afterDate);

    /** kbo_game_id 설정 (박스스코어 수집 시) */
    void updateKboGameId(@Param("id") Long id, @Param("kboGameId") String kboGameId);

    /** 기간 내 완료된 경기 조회 (박스스코어 크롤러용) */
    List<Game> findFinishedByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 상태별 ID 목록 조회 (매치업 재구성용) */
    List<Long> findIdsByStatus(@Param("status") GameStatus status);

    /** 박스스코어가 없는 완료된 경기 조회 (증분 수집용) */
    List<Game> findFinishedWithoutBoxScore();

    /** 팀별 평균 완료 경기수 (규정타석/이닝 계산 기준) */
    int getAverageTeamGameCount();
}
