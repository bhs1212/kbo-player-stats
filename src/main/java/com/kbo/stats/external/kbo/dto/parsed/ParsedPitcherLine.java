package com.kbo.stats.external.kbo.dto.parsed;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ParsedPitcherLine {
    String     teamSide;            // "AWAY" | "HOME"
    int        pitchOrder;          // 1 = 선발
    String     playerName;
    String     appearanceLabel;     // "선발" 또는 "7.9" (7회 9번타자 때 등판)
    String     result;              // WIN | LOSE | SAVE | HOLD | NONE
    int        seasonW;
    int        seasonL;
    int        seasonS;
    int        inningsPitchedOuts;  // 아웃카운트 정수 (6.2이닝=20)
    int        battersFaced;
    int        pitches;
    int        atBatsAgainst;
    int        hitsAgainst;
    int        homeRunsAgainst;
    int        walksHbp;            // 4사구
    int        strikeouts;
    int        runsAllowed;
    int        earnedRuns;
    BigDecimal seasonEra;           // null 허용
}
