package com.kbo.stats.external.kbo.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InningOutsConverter {

    private static final Pattern INNING_PATTERN = Pattern.compile("^(\\d+)?\\s*(?:(\\d)/3)?$");

    private InningOutsConverter() {}

    /**
     * KBO 이닝 문자열을 아웃카운트 정수로 변환한다.
     * <ul>
     *   <li>"6"     → 18 (6이닝 × 3아웃)</li>
     *   <li>"1/3"   → 1</li>
     *   <li>"2/3"   → 2</li>
     *   <li>"6 1/3" → 19</li>
     *   <li>"6 2/3" → 20</li>
     *   <li>null/""  → 0</li>
     * </ul>
     */
    public static int convert(String inningStr) {
        if (inningStr == null || inningStr.isBlank()) return 0;
        String s = KboTextCleaner.clean(inningStr);
        if (s == null || s.isEmpty()) return 0;
        Matcher m = INNING_PATTERN.matcher(s);
        if (!m.matches()) return 0;
        int fullInnings = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
        int fractional = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        return fullInnings * 3 + fractional;
    }
}
