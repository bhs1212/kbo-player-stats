package com.kbo.stats.external.kbo.dto.parsed;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ParsedInningScore {
    int     inning;
    String  teamSide;  // "AWAY" | "HOME"
    Integer score;     // null = 미진행 또는 연장 미사용
}
