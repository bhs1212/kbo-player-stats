package com.kbo.stats.controller;

import com.kbo.stats.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final PlayerService playerService;

    @GetMapping
    public String ranking(@RequestParam(defaultValue = "30") int limit, Model model) {
        model.addAttribute("battingRanking",  playerService.getBattingRanking(limit));
        model.addAttribute("homeRunRanking",  playerService.getHomeRunRanking(limit));
        model.addAttribute("rbiRanking",      playerService.getRbiRanking(limit));
        model.addAttribute("eraRanking",      playerService.getEraRanking(limit));
        model.addAttribute("winsRanking",     playerService.getWinsRanking(limit));
        model.addAttribute("limit", limit);
        return "ranking/index";
    }
}
