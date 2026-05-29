package com.kbo.stats.service;

import com.kbo.stats.domain.MatchupResultCategory;
import com.kbo.stats.domain.Player;
import com.kbo.stats.dto.*;
import com.kbo.stats.mapper.GameMatchupMapper;
import com.kbo.stats.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchupService {

    private final GameMatchupMapper matchupMapper;
    private final PlayerMapper playerMapper;

    @Cacheable(value = "matchup", key = "#batterName + ':' + #pitcherName")
    public MatchupSummaryDto getMatchup(String batterName, String pitcherName) {
        List<MatchupRecordRow> rows = matchupMapper.findByBatterAndPitcher(batterName, pitcherName);
        return aggregate(batterName, pitcherName, rows);
    }

    public PlayerMatchupListDto getBatterMatchups(String batterName) {
        List<MatchupRecordRow> rows = matchupMapper.findByBatter(batterName);

        List<MatchupSummaryDto> summaries = rows.stream()
                .collect(Collectors.groupingBy(MatchupRecordRow::getPitcherName, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> aggregate(batterName, e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(MatchupSummaryDto::getPlateAppearances).reversed())
                .collect(Collectors.toList());

        return PlayerMatchupListDto.builder()
                .playerName(batterName)
                .playerType("BATTER")
                .matchups(summaries)
                .build();
    }

    // ── player_id 기반 조회 (동명이인 정확 분리) ─────────────────

    @Cacheable(value = "matchup", key = "'id:' + #batterId + ':' + #pitcherId")
    public MatchupSummaryDto getMatchupByIds(Long batterId, Long pitcherId) {
        List<MatchupRecordRow> rows = matchupMapper.findByBatterIdAndPitcherId(batterId, pitcherId);
        String batterName  = playerMapper.findById(batterId).map(Player::getName).orElse("(unknown)");
        String pitcherName = playerMapper.findById(pitcherId).map(Player::getName).orElse("(unknown)");
        return aggregate(batterName, pitcherName, rows);
    }

    public PlayerMatchupListDto getBatterMatchupsById(Long batterId) {
        Player batter = playerMapper.findById(batterId)
                .orElseThrow(() -> new IllegalArgumentException("선수 미존재: " + batterId));
        List<MatchupRecordRow> rows = matchupMapper.findByBatterId(batterId);
        List<MatchupSummaryDto> summaries = rows.stream()
                .collect(Collectors.groupingBy(MatchupRecordRow::getPitcherName, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> aggregate(batter.getName(), e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(MatchupSummaryDto::getPlateAppearances).reversed())
                .collect(Collectors.toList());
        return PlayerMatchupListDto.builder()
                .playerName(batter.getName())
                .playerType("BATTER")
                .matchups(summaries)
                .build();
    }

    public PlayerMatchupListDto getPitcherMatchupsById(Long pitcherId) {
        Player pitcher = playerMapper.findById(pitcherId)
                .orElseThrow(() -> new IllegalArgumentException("선수 미존재: " + pitcherId));
        List<MatchupRecordRow> rows = matchupMapper.findByPitcherId(pitcherId);
        List<MatchupSummaryDto> summaries = rows.stream()
                .collect(Collectors.groupingBy(MatchupRecordRow::getBatterName, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> aggregate(e.getKey(), pitcher.getName(), e.getValue()))
                .sorted(Comparator.comparing(MatchupSummaryDto::getPlateAppearances).reversed())
                .collect(Collectors.toList());
        return PlayerMatchupListDto.builder()
                .playerName(pitcher.getName())
                .playerType("PITCHER")
                .matchups(summaries)
                .build();
    }

    public PlayerMatchupListDto getPitcherMatchups(String pitcherName) {
        List<MatchupRecordRow> rows = matchupMapper.findByPitcher(pitcherName);

        List<MatchupSummaryDto> summaries = rows.stream()
                .collect(Collectors.groupingBy(MatchupRecordRow::getBatterName, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> aggregate(e.getKey(), pitcherName, e.getValue()))
                .sorted(Comparator.comparing(MatchupSummaryDto::getPlateAppearances).reversed())
                .collect(Collectors.toList());

        return PlayerMatchupListDto.builder()
                .playerName(pitcherName)
                .playerType("PITCHER")
                .matchups(summaries)
                .build();
    }

    private MatchupSummaryDto aggregate(String batter, String pitcher, List<MatchupRecordRow> rows) {
        int pa = rows.size();
        int hits = 0, homeRuns = 0, walks = 0, hbp = 0, strikeouts = 0, sacrifices = 0;
        List<MatchupRecordDto> records = new ArrayList<>();

        for (MatchupRecordRow row : rows) {
            MatchupResultCategory cat = MatchupResultCategory.classify(row.getResult());

            switch (cat) {
                case HIT       -> hits++;
                case HOME_RUN  -> { hits++; homeRuns++; }
                case WALK      -> walks++;
                case HIT_BY_PITCH -> hbp++;
                case STRIKEOUT -> strikeouts++;
                case SACRIFICE -> sacrifices++;
                default        -> {}
            }

            records.add(MatchupRecordDto.builder()
                    .gameId(row.getGameId())
                    .gameDate(row.getGameDate())
                    .inning(row.getInning())
                    .atBatOrder(row.getAtBatOrder())
                    .batterName(row.getBatterName())
                    .pitcherName(row.getPitcherName())
                    .result(row.getResult())
                    .resultCategory(cat.name())
                    .build());
        }

        int atBats = pa - walks - hbp - sacrifices;
        BigDecimal avg = atBats > 0
                ? BigDecimal.valueOf(hits).divide(BigDecimal.valueOf(atBats), 3, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal obp = pa > 0
                ? BigDecimal.valueOf(hits + walks + hbp).divide(BigDecimal.valueOf(pa), 3, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return MatchupSummaryDto.builder()
                .batterName(batter)
                .pitcherName(pitcher)
                .plateAppearances(pa)
                .atBats(atBats)
                .hits(hits)
                .homeRuns(homeRuns)
                .walks(walks)
                .strikeouts(strikeouts)
                .avg(avg)
                .obp(obp)
                .records(records)
                .build();
    }
}
