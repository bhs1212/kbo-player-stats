package com.kbo.stats.external.kbo.parser;

import org.jsoup.Jsoup;

public final class KboTextCleaner {

    /** U+00A0 NON-BREAKING SPACE — Jsoup.text()가 &amp;nbsp;를 이 문자로 변환함 */
    private static final char NBSP = (char) 0x00A0;

    private KboTextCleaner() {}

    public static String clean(String raw) {
        if (raw == null) return null;
        String text = Jsoup.parse(raw).text();
        return text.replace(NBSP, ' ').trim();
    }

    /** clean() 결과가 빈 문자열이면 null 반환. */
    public static String cleanOrNull(String raw) {
        String result = clean(raw);
        return (result == null || result.isEmpty()) ? null : result;
    }
}
