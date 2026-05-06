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

    // 타율 랭킹 (타자)
    List<Player> findBattingRanking(@Param("limit") int limit);

    // 홈런 랭킹 (타자)
    List<Player> findHomeRunRanking(@Param("limit") int limit);

    // 방어율 랭킹 (투수)
    List<Player> findEraRanking(@Param("limit") int limit);

    // 타점 랭킹 (타자)
    List<Player> findRbiRanking(@Param("limit") int limit);

    // 승리 랭킹 (투수)
    List<Player> findWinsRanking(@Param("limit") int limit);

    // 팀별 통계
    List<TeamStatDto> findTeamStats();

    // 팀 목록 (중복 제거)
    List<String> findAllTeams();
}
