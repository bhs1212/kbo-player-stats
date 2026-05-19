package com.kbo.stats.external.kbo;

import java.util.Arrays;

public enum KboTeamCode {
    KIA    ("KIA", "HT"),
    DOOSAN ("두산", "OB"),
    LG     ("LG",  "LG"),
    SAMSUNG("삼성", "SS"),
    SSG    ("SSG", "SK"),
    KT     ("KT",  "KT"),
    LOTTE  ("롯데", "LT"),
    HANWHA ("한화", "HH"),
    NC     ("NC",  "NC"),
    KIWOOM ("키움", "WO");

    private final String koreanName;
    private final String kboCode;

    KboTeamCode(String koreanName, String kboCode) {
        this.koreanName = koreanName;
        this.kboCode    = kboCode;
    }

    /** "롯데" → "LT" */
    public static String toKboCode(String koreanName) {
        return Arrays.stream(values())
                .filter(t -> t.koreanName.equals(koreanName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 팀명: " + koreanName))
                .kboCode;
    }

    /** "LT" → "롯데" */
    public static String fromKboCode(String kboCode) {
        return Arrays.stream(values())
                .filter(t -> t.kboCode.equals(kboCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 KBO 코드: " + kboCode))
                .koreanName;
    }

    public String getKoreanName() { return koreanName; }
    public String getKboCode()    { return kboCode; }
}
