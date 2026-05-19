package com.kbo.stats.external.kbo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class KboGameIdBuilder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private KboGameIdBuilder() {}

    /**
     * KBO gameId를 생성한다.
     * 예: (2026-05-17, "롯데", "두산") → "20260517LTOB0"
     * 마지막 자리 0 = 정규 단일 경기 (더블헤더 1차=1, 2차=2)
     */
    public static String build(LocalDate date, String awayTeam, String homeTeam) {
        String dateStr   = date.format(DATE_FMT);
        String awayCode  = KboTeamCode.toKboCode(awayTeam);
        String homeCode  = KboTeamCode.toKboCode(homeTeam);
        return dateStr + awayCode + homeCode + "0";
    }
}
