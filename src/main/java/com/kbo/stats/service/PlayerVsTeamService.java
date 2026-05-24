package com.kbo.stats.service;

import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import com.kbo.stats.dto.PlayerVsTeamDto;
import com.kbo.stats.mapper.GameBatterLogMapper;
import com.kbo.stats.mapper.GamePitcherLogMapper;
import com.kbo.stats.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerVsTeamService {

    private final PlayerMapper playerMapper;
    private final GameBatterLogMapper gameBatterLogMapper;
    private final GamePitcherLogMapper gamePitcherLogMapper;

    @Cacheable(value = "playerVsTeam", key = "#playerId")
    public List<PlayerVsTeamDto> findByPlayerId(Long playerId) {
        Player player = playerMapper.findById(playerId).orElse(null);
        ;
        if (player == null) {
            log.warn("선수 ID {} 를 찾을 수 없어 상대 팀별 전적을 조회하지 않습니다.", playerId);
            return Collections.emptyList();
        }

        if (player.getPlayerType() == PlayerType.BATTER) {
            log.debug("타자 '{}' 상대 팀별 전적 조회", player.getName());
            return gameBatterLogMapper.aggregateBatterVsTeams(player.getName());
        } else {
            log.debug("투수 '{}' 상대 팀별 전적 조회", player.getName());
            return gamePitcherLogMapper.aggregatePitcherVsTeams(player.getName());
        }
    }
}
