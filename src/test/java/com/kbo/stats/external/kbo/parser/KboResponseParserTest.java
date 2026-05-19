package com.kbo.stats.external.kbo.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbo.stats.external.kbo.dto.KboBoxScoreResponse;
import com.kbo.stats.external.kbo.dto.KboScoreBoardResponse;
import com.kbo.stats.external.kbo.dto.parsed.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KboResponseParserTest {

    private static KboResponseParser parser;
    private static ParsedBoxScore    result;

    @BeforeAll
    static void setup() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        parser = new KboResponseParser(mapper);

        KboScoreBoardResponse sb = mapper.readValue(
                new File("docs/kbo-api-sample/scoreboard-20260517LTOB0.json"),
                KboScoreBoardResponse.class);

        KboBoxScoreResponse bs = mapper.readValue(
                new File("docs/kbo-api-sample/boxscore-20260517LTOB0.json"),
                KboBoxScoreResponse.class);

        result = parser.parseAll(sb, bs);
    }

    // ─── meta ───

    @Test
    void meta_팀명_점수_구장() {
        ParsedGameMeta meta = result.getMeta();
        assertThat(meta.getHomeTeamName()).isEqualTo("두산");
        assertThat(meta.getAwayTeamName()).isEqualTo("롯데");
        assertThat(meta.getHomeScore()).isEqualTo(8);
        assertThat(meta.getAwayScore()).isEqualTo(4);
        assertThat(meta.getStadium()).isEqualTo("잠실");
    }

    @Test
    void meta_관중수_시간() {
        ParsedGameMeta meta = result.getMeta();
        assertThat(meta.getCrowdCount()).isEqualTo(23750);
        assertThat(meta.getStartTime()).isEqualTo("14:00");
        assertThat(meta.getUseTime()).isEqualTo("2:44");
    }

    @Test
    void meta_R_H_E() {
        ParsedGameMeta meta = result.getMeta();
        // AWAY(롯데): 안타 10, 실책 2, 4사구 0
        assertThat(meta.getAwayHits()).isEqualTo(10);
        assertThat(meta.getAwayErrors()).isEqualTo(2);
        assertThat(meta.getAwayWalks()).isEqualTo(0);
        // HOME(두산): 안타 11, 실책 1, 4사구 2
        assertThat(meta.getHomeHits()).isEqualTo(11);
        assertThat(meta.getHomeErrors()).isEqualTo(1);
        assertThat(meta.getHomeWalks()).isEqualTo(2);
    }

    @Test
    void meta_심판_결승타() {
        ParsedGameMeta meta = result.getMeta();
        assertThat(meta.getFinishingHit()).isEqualTo("없음");
        assertThat(meta.getUmpires()).isNotBlank();
        assertThat(meta.getUmpires()).contains("최수원");
    }

    // ─── inningScores ───

    @Test
    void 이닝스코어_원정4회_홈7회() {
        List<ParsedInningScore> scores = result.getInningScores();

        ParsedInningScore away4 = scores.stream()
                .filter(s -> "AWAY".equals(s.getTeamSide()) && s.getInning() == 4)
                .findFirst().orElseThrow();
        assertThat(away4.getScore()).isEqualTo(1);

        ParsedInningScore home7 = scores.stream()
                .filter(s -> "HOME".equals(s.getTeamSide()) && s.getInning() == 7)
                .findFirst().orElseThrow();
        assertThat(home7.getScore()).isEqualTo(7);
    }

    @Test
    void 이닝스코어_미사용이닝은null() {
        List<ParsedInningScore> scores = result.getInningScores();
        ParsedInningScore away10 = scores.stream()
                .filter(s -> "AWAY".equals(s.getTeamSide()) && s.getInning() == 10)
                .findFirst().orElseThrow();
        assertThat(away10.getScore()).isNull();
    }

    // ─── events ───

    @Test
    void 홈런이벤트_4건() {
        List<ParsedGameEvent> hrs = result.getEvents().stream()
                .filter(e -> "HOME_RUN".equals(e.getEventType()))
                .toList();
        assertThat(hrs).hasSize(4);
    }

    @Test
    void 홈런_한동희() {
        ParsedGameEvent hr = result.getEvents().stream()
                .filter(e -> "HOME_RUN".equals(e.getEventType()) && "한동희".equals(e.getPlayerName()))
                .findFirst().orElseThrow();
        assertThat(hr.getSeasonCount()).isEqualTo(2);
        assertThat(hr.getInning()).isEqualTo(4);
        assertThat(hr.getRuns()).isEqualTo(1);
        assertThat(hr.getOpponentPitcher()).isEqualTo("최승용");
    }

    @Test
    void 홈런_강승호_김민석_레이예스() {
        List<ParsedGameEvent> hrs = result.getEvents().stream()
                .filter(e -> "HOME_RUN".equals(e.getEventType()))
                .toList();

        assertThat(hrs).anySatisfy(e -> {
            assertThat(e.getPlayerName()).isEqualTo("강승호");
            assertThat(e.getSeasonCount()).isEqualTo(1);
            assertThat(e.getInning()).isEqualTo(5);
            assertThat(e.getRuns()).isEqualTo(1);
            assertThat(e.getOpponentPitcher()).isEqualTo("로드리게스");
        });
        assertThat(hrs).anySatisfy(e -> {
            assertThat(e.getPlayerName()).isEqualTo("김민석");
            assertThat(e.getSeasonCount()).isEqualTo(2);
            assertThat(e.getInning()).isEqualTo(7);
            assertThat(e.getRuns()).isEqualTo(3);
            assertThat(e.getOpponentPitcher()).isEqualTo("최이준");
        });
        assertThat(hrs).anySatisfy(e -> {
            assertThat(e.getPlayerName()).isEqualTo("레이예스");
            assertThat(e.getSeasonCount()).isEqualTo(7);
            assertThat(e.getInning()).isEqualTo(8);
            assertThat(e.getRuns()).isEqualTo(1);
            assertThat(e.getOpponentPitcher()).isEqualTo("최준호");
        });
    }

    @Test
    void rawText에_HTML잔재_없음() {
        result.getEvents().forEach(e ->
                assertThat(e.getRawText())
                        .doesNotContain("&nbsp;")
                        .doesNotContain("<span")
                        .doesNotContain("\r\n"));
    }

    // ─── pitchers ───

    @Test
    void 원정투수_로드리게스_선발_패_6이닝() {
        ParsedPitcherLine p = result.getAwayPitchers().stream()
                .filter(pl -> "로드리게스".equals(pl.getPlayerName()))
                .findFirst().orElseThrow();
        assertThat(p.getPitchOrder()).isEqualTo(1);
        assertThat(p.getAppearanceLabel()).isEqualTo("선발");
        assertThat(p.getResult()).isEqualTo("LOSE");
        assertThat(p.getInningsPitchedOuts()).isEqualTo(18);  // 6이닝
        assertThat(p.getPitches()).isEqualTo(109);
    }

    @Test
    void 홈투수_최준호_승() {
        ParsedPitcherLine p = result.getHomePitchers().stream()
                .filter(pl -> "최준호".equals(pl.getPlayerName()))
                .findFirst().orElseThrow();
        assertThat(p.getResult()).isEqualTo("WIN");
    }

    @Test
    void 투수_NONE결과() {
        // 정철원은 결과 없음(&nbsp;) → NONE
        ParsedPitcherLine p = result.getAwayPitchers().stream()
                .filter(pl -> "정철원".equals(pl.getPlayerName()))
                .findFirst().orElseThrow();
        assertThat(p.getResult()).isEqualTo("NONE");
        assertThat(p.getInningsPitchedOuts()).isEqualTo(2);  // 2/3이닝
    }

    // ─── batters ───

    @Test
    void 타자라인업_원정팀() {
        assertThat(result.getAwayBatters()).hasSizeGreaterThanOrEqualTo(9);
        ParsedBatterLine first = result.getAwayBatters().get(0);
        assertThat(first.getTeamSide()).isEqualTo("AWAY");
        assertThat(first.getBattingOrder()).isEqualTo(1);
        assertThat(first.getPlayerName()).isNotBlank();
    }

    @Test
    void 타자_inningResults_HTML_정리됨() {
        result.getAwayBatters().forEach(b ->
                b.getInningResults().stream()
                        .filter(r -> r != null)
                        .forEach(r -> assertThat(r)
                                .doesNotContain("&nbsp;")
                                .doesNotContain("<")));
    }
}
