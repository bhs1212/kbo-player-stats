package com.kbo.stats.mapper;

import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.PlayerSearchDto;
import com.kbo.stats.dto.TeamStatDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PlayerMapper {

    // 전체 목록 (검색 + 페이징)
    List<Player> findAll(PlayerSearchDto searchDto);

    // 전체 카운트
    long countAll(PlayerSearchDto searchDto);

    // 단건 조회
    Optional<Player> findById(Long id);

    // 이름+팀으로 존재 여부 확인 (크롤링 시 중복 체크)
    Optional<Player> findByNameAndTeam(@Param("name") String name, @Param("team") String team);

    // 등록
    void insert(Player player);

    // 수정
    void update(Player player);

    // 삭제
    void deleteById(Long id);

    // 전체 삭제 (크롤링 전 초기화)
    void deleteAll();

    // 도루 단건 업데이트
    void updateStolenBases(@Param("name") String name, @Param("team") String team,
                           @Param("stolenBases") Integer stolenBases);

    // 세이브/홀드 단건 업데이트 (구원투수 크롤링용)
    void updateSavesAndHolds(@Param("name") String name, @Param("team") String team,
                              @Param("saves") Integer saves, @Param("holds") Integer holds);

    // 타율 랭킹 (타자)
    List<Player> findBattingRanking(@Param("limit") int limit);

    // 홈런 랭킹 (타자)
    List<Player> findHomeRunRanking(@Param("limit") int limit);

    // 타점 랭킹 (타자)
    List<Player> findRbiRanking(@Param("limit") int limit);

    // 도루 랭킹 (타자)
    List<Player> findStolenBasesRanking(@Param("limit") int limit);

    // 방어율 랭킹 (투수)
    List<Player> findEraRanking(@Param("limit") int limit);

    // 승리 랭킹 (투수)
    List<Player> findWinsRanking(@Param("limit") int limit);

    // 세이브 랭킹 (투수)
    List<Player> findSavesRanking(@Param("limit") int limit);

    // 홀드 랭킹 (투수)
    List<Player> findHoldsRanking(@Param("limit") int limit);

    // 팀별 통계
    List<TeamStatDto> findTeamStats();

    // 팀 목록 (중복 제거)
    List<String> findAllTeams();
}
