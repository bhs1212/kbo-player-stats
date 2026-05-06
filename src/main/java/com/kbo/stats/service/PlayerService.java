package com.kbo.stats.service;

import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.PageDto;
import com.kbo.stats.dto.PlayerFormDto;
import com.kbo.stats.dto.PlayerSearchDto;
import com.kbo.stats.dto.TeamStatDto;
import com.kbo.stats.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerMapper playerMapper;

    public PageDto<Player> search(PlayerSearchDto searchDto) {
        List<Player> content = playerMapper.findAll(searchDto);
        long total = playerMapper.countAll(searchDto);
        return new PageDto<>(content, searchDto.getPage(), searchDto.getSize(), total);
    }

    public Player findById(Long id) {
        return playerMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("선수를 찾을 수 없습니다. id=" + id));
    }

    public List<String> findAllTeams() {
        return playerMapper.findAllTeams();
    }

    @Transactional
    public Long save(PlayerFormDto form) {
        Player player = form.toEntity();
        playerMapper.insert(player);
        log.info("선수 등록: {}", player.getName());
        return player.getId();
    }

    @Transactional
    public void update(Long id, PlayerFormDto form) {
        findById(id); // 존재 확인
        form.setId(id);
        playerMapper.update(form.toEntity());
        log.info("선수 수정: id={}", id);
    }

    @Transactional
    public void delete(Long id) {
        findById(id);
        playerMapper.deleteById(id);
        log.info("선수 삭제: id={}", id);
    }

    // 랭킹
    public List<Player> getBattingRanking(int limit) {
        return playerMapper.findBattingRanking(limit);
    }

    public List<Player> getHomeRunRanking(int limit) {
        return playerMapper.findHomeRunRanking(limit);
    }

    public List<Player> getRbiRanking(int limit) {
        return playerMapper.findRbiRanking(limit);
    }

    public List<Player> getEraRanking(int limit) {
        return playerMapper.findEraRanking(limit);
    }

    public List<Player> getWinsRanking(int limit) {
        return playerMapper.findWinsRanking(limit);
    }

    // 차트용 팀별 통계
    public List<TeamStatDto> getTeamStats() {
        return playerMapper.findTeamStats();
    }

    @Transactional
    public void deleteAll() {
        playerMapper.deleteAll();
        log.info("선수 전체 삭제 완료");
    }

    // 크롤링/CSV 임포트 시 사용: upsert
    @Transactional
    public void saveOrUpdate(Player player) {
        playerMapper.findByNameAndTeam(player.getName(), player.getTeam())
                .ifPresentOrElse(
                        existing -> {
                            player.setId(existing.getId());
                            playerMapper.update(player);
                        },
                        () -> playerMapper.insert(player)
                );
    }
}
