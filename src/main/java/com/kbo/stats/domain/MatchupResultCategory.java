package com.kbo.stats.domain;

public enum MatchupResultCategory {
    HIT,
    HOME_RUN,
    WALK,
    HIT_BY_PITCH,
    STRIKEOUT,
    SACRIFICE,
    REACHED_ON_ERROR,
    OUT,
    OTHER;

    public static MatchupResultCategory classify(String result) {
        if (result == null || result.isBlank()) return OTHER;
        String r = result.trim();

        // 희생타 먼저 (홈런/안타와 키워드 겹침 방지)
        if (r.contains("희")) return SACRIFICE;

        // 홈런
        if (r.contains("홈")) return HOME_RUN;

        // 사사구
        if (r.contains("4구")) return WALK;
        if (r.contains("사구")) return HIT_BY_PITCH;

        // 삼진
        if (r.equals("삼진") || r.equals("삼") || r.endsWith("삼")) return STRIKEOUT;

        // 실책 출루
        if (r.contains("실")) return REACHED_ON_ERROR;

        // 안타 (1루타: "X안", 2루타: "X2", 3루타: "X3")
        if (r.endsWith("안") || r.matches(".*[좌중우123]2$") || r.matches(".*[좌중우]3$")) {
            return HIT;
        }

        // 그 외 (땅볼/비거리/병살/플라이 등) → OUT
        if (r.contains("땅") || r.contains("비") || r.contains("병") || r.contains("플")) {
            return OUT;
        }

        return OTHER;
    }

    /** 정식 타수(at_bat)에 포함되는지 — 볼넷/사구/희생타는 제외 */
    public boolean countsAsAtBat() {
        return this != WALK && this != HIT_BY_PITCH && this != SACRIFICE;
    }

    public boolean isHit() {
        return this == HIT || this == HOME_RUN;
    }
}
