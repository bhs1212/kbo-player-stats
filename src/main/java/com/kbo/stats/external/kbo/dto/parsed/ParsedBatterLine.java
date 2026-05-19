package com.kbo.stats.external.kbo.dto.parsed;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class ParsedBatterLine {
    String       teamSide;      // "AWAY" | "HOME"
    int          battingOrder;
    String       position;      // 중/좌/우/一/二/三/유/포/지/타
    String       playerName;
    int          atBats;
    int          hits;
    int          rbi;
    int          walks;
    BigDecimal   seasonAvg;     // null 허용
    List<String> inningResults; // 이닝별 타격결과, null = 해당 이닝 타석 없음
}
