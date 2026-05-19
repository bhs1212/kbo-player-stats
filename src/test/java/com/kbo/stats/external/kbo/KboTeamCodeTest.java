package com.kbo.stats.external.kbo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class KboTeamCodeTest {

    @Test
    void 한국어_to_KBO코드() {
        assertThat(KboTeamCode.toKboCode("롯데")).isEqualTo("LT");
        assertThat(KboTeamCode.toKboCode("두산")).isEqualTo("OB");
        assertThat(KboTeamCode.toKboCode("KIA")).isEqualTo("HT");
        assertThat(KboTeamCode.toKboCode("삼성")).isEqualTo("SS");
        assertThat(KboTeamCode.toKboCode("SSG")).isEqualTo("SK");
        assertThat(KboTeamCode.toKboCode("KT")).isEqualTo("KT");
        assertThat(KboTeamCode.toKboCode("LG")).isEqualTo("LG");
        assertThat(KboTeamCode.toKboCode("한화")).isEqualTo("HH");
        assertThat(KboTeamCode.toKboCode("NC")).isEqualTo("NC");
        assertThat(KboTeamCode.toKboCode("키움")).isEqualTo("WO");
    }

    @Test
    void KBO코드_to_한국어() {
        assertThat(KboTeamCode.fromKboCode("LT")).isEqualTo("롯데");
        assertThat(KboTeamCode.fromKboCode("OB")).isEqualTo("두산");
        assertThat(KboTeamCode.fromKboCode("HT")).isEqualTo("KIA");
        assertThat(KboTeamCode.fromKboCode("SK")).isEqualTo("SSG");
    }

    @Test
    void 잘못된_팀명_예외() {
        assertThatThrownBy(() -> KboTeamCode.toKboCode("없는팀"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 잘못된_코드_예외() {
        assertThatThrownBy(() -> KboTeamCode.fromKboCode("XX"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
