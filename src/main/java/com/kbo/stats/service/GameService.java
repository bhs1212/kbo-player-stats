package com.kbo.stats.service;

import com.kbo.stats.domain.Game;
import com.kbo.stats.domain.GameStatus;
import com.kbo.stats.dto.CalendarEventDto;
import com.kbo.stats.mapper.GameMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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

    public List<Game> getRecentByTeam(String team, int limit) {
        return gameMapper.findRecentByTeam(team, limit);
    }

    public java.util.Optional<Game> getNextByTeam(String team, LocalDate afterDate) {
        return gameMapper.findNextByTeam(team, afterDate);
    }

    @Transactional
    public void upsert(Game game) {
        gameMapper.upsert(game);
    }

    public int countByStatus(GameStatus status) {
        return gameMapper.countByStatus(status);
    }

    public List<CalendarEventDto> findEventsForCalendar(LocalDate start, LocalDate end, String team) {
        List<Game> games = (team == null || team.isBlank() || "ALL".equals(team))
                ? getGamesInRange(start, end)
                : getGamesByTeam(team, start, end);
        return games.stream().map(this::toCalendarEvent).collect(Collectors.toList());
    }

    private CalendarEventDto toCalendarEvent(Game game) {
        String title;
        String color;

        switch (game.getStatus()) {
            case FINISHED:
                title = String.format("%s %d : %d %s",
                        game.getAwayTeam(), game.getAwayScore(),
                        game.getHomeScore(), game.getHomeTeam());
                color = "#6c757d";
                break;
            case IN_PROGRESS:
                if (game.getAwayScore() != null && game.getHomeScore() != null) {
                    title = String.format("%s %d : %d %s",
                            game.getAwayTeam(), game.getAwayScore(),
                            game.getHomeScore(), game.getHomeTeam());
                } else {
                    title = String.format("%s vs %s", game.getAwayTeam(), game.getHomeTeam());
                }
                color = "#198754";
                break;
            case CANCELED:
                title = String.format("%s vs %s", game.getAwayTeam(), game.getHomeTeam());
                color = "#dc3545";
                break;
            case POSTPONED:
                title = String.format("%s vs %s", game.getAwayTeam(), game.getHomeTeam());
                color = "#6c757d";
                break;
            default: // SCHEDULED
                title = String.format("%s vs %s", game.getAwayTeam(), game.getHomeTeam());
                color = "#0d6efd";
        }

        String startStr;
        String endStr = null;
        if (game.getGameTime() != null) {
            LocalDateTime startDt = LocalDateTime.of(game.getGameDate(), game.getGameTime());
            startStr = startDt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            endStr   = startDt.plusHours(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else {
            startStr = game.getGameDate().toString();
        }

        Map<String, Object> props = new HashMap<>();
        props.put("awayTeam",  game.getAwayTeam());
        props.put("homeTeam",  game.getHomeTeam());
        props.put("awayScore", game.getAwayScore());
        props.put("homeScore", game.getHomeScore());
        props.put("status",    game.getStatus().name());
        props.put("stadium",   game.getStadium());
        props.put("gameTime",  game.getGameTime() != null ? game.getGameTime().toString() : null);
        props.put("kboGameId", game.getKboGameId());

        return CalendarEventDto.builder()
                .id(game.getId())
                .title(title)
                .start(startStr)
                .end(endStr)
                .backgroundColor(color)
                .borderColor(color)
                .extendedProps(props)
                .build();
    }
}
