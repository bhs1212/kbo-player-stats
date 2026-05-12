package com.kbo.stats.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Hidden
@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "redirect:/players";
    }
}
