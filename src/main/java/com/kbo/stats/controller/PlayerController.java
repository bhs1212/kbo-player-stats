package com.kbo.stats.controller;

import com.kbo.stats.domain.BatterStats;
import com.kbo.stats.domain.PitcherStats;
import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import com.kbo.stats.dto.PageDto;
import com.kbo.stats.dto.PlayerSearchDto;
import com.kbo.stats.dto.PlayerVsTeamDto;
import com.kbo.stats.mapper.BatterStatsMapper;
import com.kbo.stats.mapper.PitcherStatsMapper;
import com.kbo.stats.service.PlayerService;
import com.kbo.stats.service.PlayerVsTeamService;
import com.kbo.stats.service.SabermetricsService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;

@Hidden
@Controller
@RequestMapping("/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService       playerService;
    private final BatterStatsMapper   batterStatsMapper;
    private final PitcherStatsMapper  pitcherStatsMapper;
    private final SabermetricsService sabermetricsService;
    private final PlayerVsTeamService playerVsTeamService;

    @GetMapping
    public String list(PlayerSearchDto searchDto, Model model) {
        if (searchDto.getPlayerType() == null) {
            searchDto.setPlayerType(PlayerType.BATTER);
        }
        PageDto<Player> page = playerService.search(searchDto);
        List<String> teams = playerService.findAllTeams();

        model.addAttribute("page", page);
        model.addAttribute("searchDto", searchDto);
        model.addAttribute("teams", teams);
        model.addAttribute("isBatter", searchDto.getPlayerType() == PlayerType.BATTER);
        return "player/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Player player = playerService.findById(id);
        model.addAttribute("player", player);

        if (player.getPlayerType() == PlayerType.BATTER) {
            BatterStats stats = batterStatsMapper.findByPlayerId(id);
            model.addAttribute("batterStats", stats);

            if (stats != null) {
                Integer hits = player.getHits();
                Integer hr   = player.getHomeRuns();
                BigDecimal avg = player.getBattingAvg() != null
                        ? BigDecimal.valueOf(player.getBattingAvg()) : null;

                BigDecimal ops    = sabermetricsService.calculateOPS(stats, hits);
                BigDecimal obp    = sabermetricsService.calculateOBP(stats, hits);
                BigDecimal slg    = sabermetricsService.calculateSLG(stats);
                BigDecimal iso    = sabermetricsService.calculateISO(stats, avg);
                BigDecimal bbPerK = sabermetricsService.calculateBBperK(stats);
                BigDecimal babip  = sabermetricsService.calculateBABIP(stats, hits, hr);
                BigDecimal woba   = sabermetricsService.calculateWOBA(stats, hits, hr);

                model.addAttribute("ops",    ops);
                model.addAttribute("obp",    obp);
                model.addAttribute("slg",    slg);
                model.addAttribute("iso",    iso);
                model.addAttribute("bbPerK", bbPerK);
                model.addAttribute("babip",  babip);
                model.addAttribute("woba",   woba);

                model.addAttribute("leagueOps",    sabermetricsService.getLeagueAverageOPS());
                model.addAttribute("leagueObp",    sabermetricsService.getLeagueAverageOBP());
                model.addAttribute("leagueSlg",    sabermetricsService.getLeagueAverageSLG());
                model.addAttribute("leagueIso",    sabermetricsService.getLeagueAverageISO());
                model.addAttribute("leagueBbPerK", sabermetricsService.getLeagueAverageBBperK());
                model.addAttribute("leagueBabip",  sabermetricsService.getLeagueAverageBABIP());
                model.addAttribute("leagueWoba",   sabermetricsService.getLeagueAverageWOBA());

                model.addAttribute("avgPercentile", sabermetricsService.getBatterPercentileAvg(avg));
                model.addAttribute("opsPercentile", sabermetricsService.getBatterPercentileOps(ops));
                model.addAttribute("isoPercentile", sabermetricsService.getBatterPercentileIso(iso));
                model.addAttribute("obpPercentile", sabermetricsService.getBatterPercentileObp(obp));
                model.addAttribute("bbkPercentile", sabermetricsService.getBatterPercentileBBperK(bbPerK));
            }

        } else if (player.getPlayerType() == PlayerType.PITCHER) {
            PitcherStats stats = pitcherStatsMapper.findByPlayerId(id);
            model.addAttribute("pitcherStats", stats);

            if (stats != null) {
                BigDecimal fipConst = sabermetricsService.getLeagueFIPConstant();

                BigDecimal whip   = sabermetricsService.calculateWHIP(stats);
                BigDecimal kPer9  = sabermetricsService.calculateKper9(stats);
                BigDecimal bbPer9 = sabermetricsService.calculateBBper9(stats);
                BigDecimal kPerBb = sabermetricsService.calculateKperBB(stats);
                BigDecimal hrPer9 = sabermetricsService.calculateHRper9(stats);
                BigDecimal fip    = sabermetricsService.calculateFIP(stats, fipConst);

                model.addAttribute("whip",   whip);
                model.addAttribute("kPer9",  kPer9);
                model.addAttribute("bbPer9", bbPer9);
                model.addAttribute("kPerBb", kPerBb);
                model.addAttribute("hrPer9", hrPer9);
                model.addAttribute("fip",    fip);

                model.addAttribute("leagueWhip",   sabermetricsService.getLeagueAverageWHIP());
                model.addAttribute("leagueEra",    sabermetricsService.getLeagueAverageERA());
                model.addAttribute("leagueKper9",  sabermetricsService.getLeagueAverageKper9());
                model.addAttribute("leagueBbPer9", sabermetricsService.getLeagueAverageBBper9());
                model.addAttribute("leagueKperBb", sabermetricsService.getLeagueAverageKperBB());
                model.addAttribute("leagueHrPer9", sabermetricsService.getLeagueAverageHRper9());

                model.addAttribute("k9Percentile",   sabermetricsService.getPitcherPercentileK9(kPer9));
                model.addAttribute("bb9Percentile",  sabermetricsService.getPitcherPercentileBB9(bbPer9));
                model.addAttribute("outsPercentile", sabermetricsService.getPitcherPercentileOuts(stats.getInningsOuts()));
                model.addAttribute("whipPercentile", sabermetricsService.getPitcherPercentileWhip(whip));
                model.addAttribute("hr9Percentile",  sabermetricsService.getPitcherPercentileHR9(hrPer9));
            }
        }

        List<PlayerVsTeamDto> vsTeams = playerVsTeamService.findByPlayerId(id);
        model.addAttribute("vsTeams", vsTeams);

        return "player/detail";
    }
}
