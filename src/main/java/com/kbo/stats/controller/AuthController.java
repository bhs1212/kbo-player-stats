package com.kbo.stats.controller;

import com.kbo.stats.dto.SignUpDto;
import com.kbo.stats.service.PlayerService;
import com.kbo.stats.service.UserAccountService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Hidden
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserAccountService userAccountService;
    private final PlayerService playerService;

    /** 로그인 페이지 */
    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "signupSuccess", required = false) String signupSuccess,
            Model model) {
        if (error != null) {
            model.addAttribute("loginError", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (signupSuccess != null) {
            model.addAttribute("signupSuccess", true);
        }
        return "login";
    }

    /** 회원가입 폼 */
    @GetMapping("/signup")
    public String signUpForm(Model model) {
        model.addAttribute("signUpDto", new SignUpDto());
        model.addAttribute("teams", playerService.findAllTeams());
        return "auth/signup";
    }

    /** 회원가입 처리 */
    @PostMapping("/signup")
    public String signUp(@Valid @ModelAttribute("signUpDto") SignUpDto dto,
                         BindingResult bindingResult,
                         Model model) {
        // Bean Validation 에러
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", playerService.findAllTeams());
            return "auth/signup";
        }

        // 비밀번호 일치 확인
        if (!dto.isPasswordMatching()) {
            bindingResult.rejectValue("passwordConfirm", "mismatch", "비밀번호가 일치하지 않습니다");
            model.addAttribute("teams", playerService.findAllTeams());
            return "auth/signup";
        }

        try {
            userAccountService.signUp(dto);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("signupFailed", e.getMessage());
            model.addAttribute("teams", playerService.findAllTeams());
            return "auth/signup";
        }

        return "redirect:/login?signupSuccess=true";
    }
}
