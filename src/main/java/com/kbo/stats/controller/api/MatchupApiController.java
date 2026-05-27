package com.kbo.stats.controller.api;

import com.kbo.stats.dto.MatchupSummaryDto;
import com.kbo.stats.dto.PlayerMatchupListDto;
import com.kbo.stats.service.MatchupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matchup")
@RequiredArgsConstructor
@Tag(name = "매치업 API")
public class MatchupApiController {

    private final MatchupService matchupService;

    @GetMapping
    @Operation(summary = "두 선수 매치업 조회")
    public ResponseEntity<MatchupSummaryDto> getMatchup(
            @RequestParam String batter,
            @RequestParam String pitcher) {
        MatchupSummaryDto result = matchupService.getMatchup(batter, pitcher);
        if (result.getPlateAppearances() == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/batter/{name}")
    @Operation(summary = "타자의 모든 상대 투수 매치업")
    public ResponseEntity<PlayerMatchupListDto> getBatterMatchups(@PathVariable String name) {
        return ResponseEntity.ok(matchupService.getBatterMatchups(name));
    }

    @GetMapping("/pitcher/{name}")
    @Operation(summary = "투수의 모든 상대 타자 매치업")
    public ResponseEntity<PlayerMatchupListDto> getPitcherMatchups(@PathVariable String name) {
        return ResponseEntity.ok(matchupService.getPitcherMatchups(name));
    }
}
