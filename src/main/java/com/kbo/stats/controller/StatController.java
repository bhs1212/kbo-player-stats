package com.kbo.stats.controller;

import com.kbo.stats.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatController {

    private final PlayerService playerService;

    @GetMapping("/chart")
    public String chart(Model model) {
        model.addAttribute("teamStats", playerService.getTeamStats());
        return "stats/chart";
    }
}
