package com.kbo.stats.service;

import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.GameStatus;
import com.kbo.stats.mapper.GameMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

    private final GameMapper gameMapper;

    public List<Game> getGamesInRange(LocalDate start, LocalDate end) {
        return gameMapper.findByDateRange(start, end);
    }

    public List<Game> getGamesByDate(LocalDate date) {
        return gameMapper.findByDate(date);
    }

    public List<Game> getGamesByTeam(String team, LocalDate start, LocalDate end) {
        return gameMapper.findByTeamAndDateRange(team, start, end);
    }

    public Game getGameById(Long id) {
        return gameMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("경기를 찾을 수 없습니다: " + id));
    }

    /** 날짜별 그룹화 (team 파라미터가 비어 있으면 전체) */
    public Map<LocalDate, List<Game>> getGamesGroupedByDate(String team, LocalDate start, LocalDate end) {
        List<Game> games = (team == null || team.isBlank())
                ? getGamesInRange(start, end)
                : getGamesByTeam(team, start, end);

        return games.stream()
                .collect(Collectors.groupingBy(
                        Game::getGameDate,
                        TreeMap::new,
                        Collectors.toList()
                ));
    }

    @Transactional
    public void upsert(Game game) {
        gameMapper.upsert(game);
    }

    public int countByStatus(GameStatus status) {
        return gameMapper.countByStatus(status);
    }
}
