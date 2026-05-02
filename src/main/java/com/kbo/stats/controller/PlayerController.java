package com.kbo.stats.controller;

import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import com.kbo.stats.dto.PageDto;
import com.kbo.stats.dto.PlayerFormDto;
import com.kbo.stats.dto.PlayerSearchDto;
import com.kbo.stats.service.CsvImportService;
import com.kbo.stats.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final CsvImportService csvImportService;

    /** 선수 목록 */
    @GetMapping
    public String list(PlayerSearchDto searchDto, Model model) {
        PageDto<Player> page = playerService.search(searchDto);
        List<String> teams = playerService.findAllTeams();

        model.addAttribute("page", page);
        model.addAttribute("searchDto", searchDto);
        model.addAttribute("teams", teams);
        model.addAttribute("playerTypes", PlayerType.values());
        return "player/list";
    }

    /** 선수 상세 */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Player player = playerService.findById(id);
        model.addAttribute("player", player);
        return "player/detail";
    }

    /** 등록 폼 */
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new PlayerFormDto());
        model.addAttribute("playerTypes", PlayerType.values());
        model.addAttribute("mode", "create");
        return "player/form";
    }

    /** 등록 처리 */
    @PostMapping
    public String create(@Valid @ModelAttribute("form") PlayerFormDto form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("playerTypes", PlayerType.values());
            model.addAttribute("mode", "create");
            return "player/form";
        }
        Long id = playerService.save(form);
        redirectAttrs.addFlashAttribute("successMessage", "선수가 등록되었습니다.");
        return "redirect:/players/" + id;
    }

    /** 수정 폼 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Player player = playerService.findById(id);
        model.addAttribute("form", PlayerFormDto.from(player));
        model.addAttribute("playerTypes", PlayerType.values());
        model.addAttribute("mode", "edit");
        return "player/form";
    }

    /** 수정 처리 */
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") PlayerFormDto form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("playerTypes", PlayerType.values());
            model.addAttribute("mode", "edit");
            return "player/form";
        }
        playerService.update(id, form);
        redirectAttrs.addFlashAttribute("successMessage", "선수 정보가 수정되었습니다.");
        return "redirect:/players/" + id;
    }

    /** 삭제 처리 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        playerService.delete(id);
        redirectAttrs.addFlashAttribute("successMessage", "선수가 삭제되었습니다.");
        return "redirect:/players";
    }

    /** CSV 임포트 */
    @PostMapping("/import")
    public String importCsv(@RequestParam("file") MultipartFile file,
                            RedirectAttributes redirectAttrs) {
        if (file.isEmpty()) {
            redirectAttrs.addFlashAttribute("errorMessage", "파일을 선택하세요.");
            return "redirect:/players";
        }
        try {
            int count = csvImportService.importFromCsv(file.getInputStream());
            redirectAttrs.addFlashAttribute("successMessage", count + "명의 선수 데이터가 임포트되었습니다.");
        } catch (IOException e) {
            redirectAttrs.addFlashAttribute("errorMessage", "CSV 임포트 실패: " + e.getMessage());
        }
        return "redirect:/players";
    }
}
