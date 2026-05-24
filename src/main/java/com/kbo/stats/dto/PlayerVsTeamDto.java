package com.kbo.stats.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerVsTeamDto {

    private String opponent;
    private Integer games;

    // 타자용 (투수일 땐 null)
    private Integer atBats;
    private Integer hits;
    private Integer rbi;
    private Integer walks;
    private BigDecimal avg;

    // 투수용 (타자일 땐 null)
    private Integer inningsOuts;
    private Integer earnedRuns;
    private Integer hitsAllowed;
    private Integer walksHbp;
    private Integer strikeouts;
    private BigDecimal era;
    private BigDecimal whip;

    public String getInningsPitchedDisplay() {
        if (inningsOuts == null) return "-";
        int whole = inningsOuts / 3;
        int rem = inningsOuts % 3;
        return whole + (rem == 1 ? "⅓" : rem == 2 ? "⅔" : "");
    }
}
