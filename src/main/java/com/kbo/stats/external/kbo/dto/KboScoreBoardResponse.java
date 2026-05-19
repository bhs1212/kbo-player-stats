package com.kbo.stats.external.kbo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * KBO GetScoreBoardScroll 응답 최상위 DTO.
 * table1/table2/table3 은 이중 JSON 문자열이므로 String 으로 수신 후 파서에서 별도 파싱.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KboScoreBoardResponse {

    @JsonProperty("LE_ID")     private int    leId;
    @JsonProperty("SR_ID")     private int    srId;
    @JsonProperty("G_ID")      private String gId;
    @JsonProperty("G_DT")      private String gDt;
    @JsonProperty("SEASON_ID") private int    seasonId;
    @JsonProperty("HOME_NM")   private String homeNm;
    @JsonProperty("HOME_ID")   private String homeId;
    @JsonProperty("AWAY_NM")   private String awayNm;
    @JsonProperty("AWAY_ID")   private String awayId;
    @JsonProperty("S_NM")      private String sNm;
    @JsonProperty("CROWD_CN")  private String crowdCn;   // "23,750" — 콤마 포함 문자열
    @JsonProperty("H_W_CN")    private int    hWCn;
    @JsonProperty("H_L_CN")    private int    hLCn;
    @JsonProperty("H_D_CN")    private int    hDCn;
    @JsonProperty("A_W_CN")    private int    aWCn;
    @JsonProperty("A_L_CN")    private int    aLCn;
    @JsonProperty("A_D_CN")    private int    aDCn;
    @JsonProperty("T_SCORE_CN") private int   tScoreCn;  // AWAY(원정) 합계
    @JsonProperty("B_SCORE_CN") private int   bScoreCn;  // HOME(홈) 합계
    @JsonProperty("START_TM")  private String startTm;
    @JsonProperty("END_TM")    private String endTm;
    @JsonProperty("USE_TM")    private String useTm;
    @JsonProperty("FULL_HOME_NM") private String fullHomeNm;
    @JsonProperty("FULL_AWAY_NM") private String fullAwayNm;

    private String table1;   // 승패 row
    private String table2;   // 이닝별 득점 (이중 JSON)
    private String table3;   // R/H/E/BB 합계 (이중 JSON)
    private int    maxInning;
    private String code;
    private String msg;
}
