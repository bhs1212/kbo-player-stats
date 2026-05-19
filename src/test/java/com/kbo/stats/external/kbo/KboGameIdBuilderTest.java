package com.kbo.stats.external.kbo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class KboGameIdBuilderTest {

    @Test
    void 롯데_두산_20260517() {
        String gameId = KboGameIdBuilder.build(LocalDate.of(2026, 5, 17), "롯데", "두산");
        assertThat(gameId).isEqualTo("20260517LTOB0");
    }

    @Test
    void KIA_삼성_20260601() {
        String gameId = KboGameIdBuilder.build(LocalDate.of(2026, 6, 1), "KIA", "삼성");
        assertThat(gameId).isEqualTo("20260601HTSS0");
    }

    @Test
    void 마지막자리는_0_고정() {
        String gameId = KboGameIdBuilder.build(LocalDate.of(2026, 5, 17), "LG", "KT");
        assertThat(gameId).endsWith("0");
        assertThat(gameId).isEqualTo("20260517LGKT0");
    }
}
