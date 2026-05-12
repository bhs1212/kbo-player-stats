package com.kbo.stats.controller;

import com.kbo.stats.service.PlayerService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Hidden
@Controller
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class RankingController {

    private static final int RANKING_FETCH_LIMIT = 100;

    private final PlayerService playerService;

    @GetMapping
    public String ranking(Model model) {
        model.addAttribute("battingRanking",      playerService.getBattingRanking(RANKING_FETCH_LIMIT));
        model.addAttribute("homeRunRanking",       playerService.getHomeRunRanking(RANKING_FETCH_LIMIT));
        model.addAttribute("rbiRanking",           playerService.getRbiRanking(RANKING_FETCH_LIMIT));
        model.addAttribute("stolenBasesRanking",   playerService.getStolenBasesRanking(RANKING_FETCH_LIMIT));
        model.addAttribute("eraRanking",           playerService.getEraRanking(RANKING_FETCH_LIMIT));
        model.addAttribute("winsRanking",          playerService.getWinsRanking(RANKING_FETCH_LIMIT));
        model.addAttribute("savesRanking",         playerService.getSavesRanking(RANKING_FETCH_LIMIT));
        model.addAttribute("holdsRanking",         playerService.getHoldsRanking(RANKING_FETCH_LIMIT));
        return "ranking/index";
    }
}
