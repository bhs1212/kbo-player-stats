package com.kbo.stats.service;

import com.kbo.stats.domain.*;
import com.kbo.stats.dto.GameDetailDto;
import com.kbo.stats.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameDetailService {

    private final GameMapper gameMapper;
    private final GameBoxScoreMapper gameBoxScoreMapper;
    private final GameInningScoreMapper gameInningScoreMapper;
    private final GameEventMapper gameEventMapper;
    private final GameBatterLogMapper gameBatterLogMapper;
    private final GamePitcherLogMapper gamePitcherLogMapper;

    @Cacheable(value = "gameDetail", key = "#gameId")
    public GameDetailDto findGameDetail(Long gameId) {
        Game game = gameMapper.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("게임 미존재: " + gameId));

        if (game.getStatus() != GameStatus.FINISHED) {
            log.info("게임 상세 조회: gameId={} status={} hasBoxScore={}", gameId, game.getStatus(), false);
            return GameDetailDto.builder()
                    .game(game)
                    .hasBoxScore(false)
                    .boxScore(null)
                    .innings(Collections.emptyList())
                    .events(Collections.emptyList())
                    .awayBatters(Collections.emptyList())
                    .homeBatters(Collections.emptyList())
                    .awayPitchers(Collections.emptyList())
                    .homePitchers(Collections.emptyList())
                    .build();
        }

        Optional<GameBoxScore> boxScoreOpt = gameBoxScoreMapper.findByGameId(gameId);
        boolean hasBoxScore = boxScoreOpt.isPresent();

        List<GameInningScore> innings = gameInningScoreMapper.findAllByGameId(gameId);
        List<GameEvent> events = gameEventMapper.findAllByGameId(gameId);
        List<GameBatterLog> batters = gameBatterLogMapper.findAllByGameId(gameId);
        List<GamePitcherLog> pitchers = gamePitcherLogMapper.findAllByGameId(gameId);

        List<GameBatterLog> awayBatters = batters.stream()
                .filter(b -> "AWAY".equals(b.getTeamSide()))
                .collect(Collectors.toList());
        List<GameBatterLog> homeBatters = batters.stream()
                .filter(b -> "HOME".equals(b.getTeamSide()))
                .collect(Collectors.toList());
        List<GamePitcherLog> awayPitchers = pitchers.stream()
                .filter(p -> "AWAY".equals(p.getTeamSide()))
                .collect(Collectors.toList());
        List<GamePitcherLog> homePitchers = pitchers.stream()
                .filter(p -> "HOME".equals(p.getTeamSide()))
                .collect(Collectors.toList());

        log.info("게임 상세 조회: gameId={} status={} hasBoxScore={}", gameId, game.getStatus(), hasBoxScore);

        return GameDetailDto.builder()
                .game(game)
                .hasBoxScore(hasBoxScore)
                .boxScore(boxScoreOpt.orElse(null))
                .innings(innings)
                .events(events)
                .awayBatters(awayBatters)
                .homeBatters(homeBatters)
                .awayPitchers(awayPitchers)
                .homePitchers(homePitchers)
                .build();
    }
}
