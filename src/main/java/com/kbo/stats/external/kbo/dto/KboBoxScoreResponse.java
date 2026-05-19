package com.kbo.stats.external.kbo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * KBO GetBoxScoreScroll 응답 최상위 DTO.
 * table 계열 필드는 모두 이중 JSON 문자열이므로 String 으로 수신.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KboBoxScoreResponse {

    private List<KboHitterTable>  arrHitter;   // [0]=AWAY, [1]=HOME
    private List<KboPitcherTable> arrPitcher;  // [0]=AWAY, [1]=HOME
    private String tableEtc;                   // 이중 JSON 문자열 (결승타/홈런/실책 등)
    private int    maxInning;
    private int    realMaxInning;
    private String code;
    private String msg;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KboHitterTable {
        private String table1;  // 타순·포지션·선수명
        private String table2;  // 이닝별 타격결과
        private String table3;  // 타수·안타·타점·볼넷·타율
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KboPitcherTable {
        private String table;   // 투수 성적 전체
    }
}
