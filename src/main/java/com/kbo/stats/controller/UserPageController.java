package com.kbo.stats.controller;

import com.kbo.stats.domain.PlayerType;
import com.kbo.stats.domain.UserAccount;
import com.kbo.stats.dto.PageDto;
import com.kbo.stats.dto.PlayerSearchDto;
import com.kbo.stats.service.PlayerService;
import com.kbo.stats.service.UserAccountService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Hidden
@Controller
@RequestMapping("/my")
@RequiredArgsConstructor
public class UserPageController {

    private final UserAccountService userAccountService;
    private final PlayerService playerService;

    /** 마이페이지 - 사용자 정보 및 응원팀 변경 폼 */
    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal UserDetails principal, Model model) {
        UserAccount user = userAccountService.findByUsername(principal.getUsername());
        model.addAttribute("user", user);
        model.addAttribute("teams", playerService.findAllTeams());
        return "user/profile";
    }

    /** 응원팀 변경 처리 */
    @PostMapping("/profile/team")
    public String updateTeam(@AuthenticationPrincipal UserDetails principal,
                             @RequestParam("favoriteTeam") String favoriteTeam,
                             RedirectAttributes redirectAttributes) {
        userAccountService.updateFavoriteTeam(principal.getUsername(), favoriteTeam);
        redirectAttributes.addFlashAttribute("successMessage", "응원 팀이 변경되었습니다.");
        return "redirect:/my/profile";
    }

    /** 내 응원팀 선수 목록 */
    @GetMapping("/team")
    public String myTeam(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(required = false) PlayerType playerType,
                         Model model) {
        UserAccount user = userAccountService.findByUsername(principal.getUsername());
        String favoriteTeam = user.getFavoriteTeam();

        PlayerSearchDto searchDto = new PlayerSearchDto();
        searchDto.setTeam(favoriteTeam);
        searchDto.setPage(page);
        // 기본값은 타자
        PlayerType resolvedType = playerType != null ? playerType : PlayerType.BATTER;
        searchDto.setPlayerType(resolvedType);

        PageDto<?> pageDto = playerService.search(searchDto);

        model.addAttribute("favoriteTeam", favoriteTeam);
        model.addAttribute("pageDto", pageDto);
        model.addAttribute("searchDto", searchDto);
        model.addAttribute("isBatter", resolvedType == PlayerType.BATTER);
        return "user/my-team";
    }
}
