package com.kbo.stats.service;

import com.kbo.stats.domain.BatterStats;
import com.kbo.stats.domain.PitcherStats;
import com.kbo.stats.domain.StatValidationLog;
import com.kbo.stats.dto.LeagueTotalsDto;
import com.kbo.stats.mapper.BatterStatsMapper;
import com.kbo.stats.mapper.PitcherStatsMapper;
import com.kbo.stats.mapper.StatValidationLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SabermetricsServiceTest {

    @Mock private BatterStatsMapper batterStatsMapper;
    @Mock private PitcherStatsMapper pitcherStatsMapper;
    @Mock private StatValidationLogMapper statValidationLogMapper;

    @InjectMocks
    private SabermetricsService sabermetricsService;

    @Captor
    private ArgumentCaptor<StatValidationLog> logCaptor;

    // ──────────────────────────────────────────────────────────────
    // OBP 테스트
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OBP 정상 케이스: (H+BB+HBP)/(AB+BB+HBP+SF)")
    void testOBP_정상케이스() {
        // H=150, BB=50, HBP=10, AB=420, SF=5
        // OBP = (150+50+10) / (420+50+10+5) = 210 / 485 = 0.433
        BatterStats stats = BatterStats.builder()
                .playerId(1L)
                .atBats(420).walks(50).hitByPitch(10).sacrificeFlies(5)
                .build();

        BigDecimal result = sabermetricsService.calculateOBP(stats, 150);

        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.433"));
    }

    @Test
    @DisplayName("OBP 분모 0이면 null 반환")
    void testOBP_분모0_null반환() {
        BatterStats stats = BatterStats.builder()
                .playerId(1L)
                .atBats(0).walks(0).hitByPitch(0).sacrificeFlies(0)
                .build();

        BigDecimal result = sabermetricsService.calculateOBP(stats, 0);

        assertThat(result).isNull();
    }

    // ──────────────────────────────────────────────────────────────
    // OPS 검증 테스트
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OPS 사이트값과 계산값 일치 → isMatch=true, 로그 INSERT")
    void testOPS_사이트값과일치() {
        // OBP = 210/485 = 0.433, SLG = 250/420 = 0.595, OPS = 1.028
        BatterStats stats = BatterStats.builder()
                .playerId(1L)
                .atBats(420).walks(50).hitByPitch(10).sacrificeFlies(5)
                .totalBases(250)
                .ops(new BigDecimal("1.028"))
                .build();
        willDoNothing().given(statValidationLogMapper).insert(any());

        boolean match = sabermetricsService.validateOPS(1L, stats, 150);

        assertThat(match).isTrue();
        then(statValidationLogMapper).should(times(1)).insert(logCaptor.capture());
        StatValidationLog log = logCaptor.getValue();
        assertThat(log.getIsMatch()).isTrue();
        assertThat(log.getMetricName()).isEqualTo("OPS");
        assertThat(log.getDiff()).isLessThanOrEqualTo(new BigDecimal("0.001"));
    }

    // ──────────────────────────────────────────────────────────────
    // ISO 테스트
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ISO 정상 계산: SLG - AVG")
    void testISO_정상계산() {
        // SLG = 250/420 = 0.595, AVG = 0.350 → ISO = 0.245
        BatterStats stats = BatterStats.builder()
                .playerId(1L)
                .atBats(420).totalBases(250)
                .build();

        BigDecimal result = sabermetricsService.calculateISO(stats, new BigDecimal("0.350"));

        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.245"));
    }

    // ──────────────────────────────────────────────────────────────
    // FIP 테스트
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FIP 리그 상수 적용: (13*HR + 3*(BB+HBP) - 2*SO) / IP + constant")
    void testFIP_리그상수적용() {
        // IP = 300 outs = 100이닝, HR=15, BB=40, HBP=5, SO=120
        // FIP = (13*15 + 3*(40+5) - 2*120) / 100 + 3.20
        //     = (195 + 135 - 240) / 100 + 3.20
        //     = 0.90 + 3.20 = 4.10
        PitcherStats stats = PitcherStats.builder()
                .playerId(1L)
                .inningsOuts(300)
                .homeRunsAllowed(15).walksAllowed(40).hbpAllowed(5).strikeouts(120)
                .build();

        BigDecimal result = sabermetricsService.calculateFIP(stats, new BigDecimal("3.20"));

        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("4.10"));
    }

    // ──────────────────────────────────────────────────────────────
    // K/9 (이닝 변환) 테스트
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("K/9: inningsOuts → IP 변환 후 계산")
    void testParseInningsOuts() {
        // inningsOuts=157 (52⅓이닝), SO=100
        // IP = 157/3 ≈ 52.3333, K/9 = 100*9/52.3333 ≈ 17.20
        PitcherStats stats = PitcherStats.builder()
                .playerId(1L)
                .inningsOuts(157).strikeouts(100)
                .build();

        BigDecimal result = sabermetricsService.calculateKper9(stats);

        assertThat(result).isNotNull();
        // 100 * 9 / (157/3) = 900 * 3 / 157 = 2700/157 ≈ 17.20
        assertThat(result).isGreaterThan(new BigDecimal("17.00"));
        assertThat(result).isLessThan(new BigDecimal("18.00"));
    }

    // ──────────────────────────────────────────────────────────────
    // WHIP 테스트
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("WHIP 불일치 시 isMatch=false, 로그에 기록")
    void testWHIP_불일치_로그기록() {
        // IP = 90이닝(270 outs), BB=30, H=80 → WHIP = 110/90 = 1.22
        // 사이트값은 1.50 → 차이 0.28 → 불일치
        PitcherStats stats = PitcherStats.builder()
                .playerId(2L)
                .inningsOuts(270).walksAllowed(30).hitsAllowed(80)
                .whip(new BigDecimal("1.50"))
                .build();
        willDoNothing().given(statValidationLogMapper).insert(any());

        boolean match = sabermetricsService.validateWHIP(2L, stats);

        assertThat(match).isFalse();
        then(statValidationLogMapper).should(times(1)).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getIsMatch()).isFalse();
    }

    // ──────────────────────────────────────────────────────────────
    // WHIP 경계값 반올림 테스트 (outs 기반 계산 정밀도 검증)
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("WHIP 1.425 경계값 → HALF_UP → 1.43 (장찬희 케이스)")
    void testWHIP_1점425_경계값_HALFUP반올림() {
        // (14+24)*3 / 80 = 114/80 = 1.425 → HALF_UP scale=2 → 1.43
        PitcherStats stats = PitcherStats.builder()
                .playerId(10L)
                .walksAllowed(14).hitsAllowed(24).inningsOuts(80)
                .build();

        BigDecimal result = sabermetricsService.calculateWHIP(stats);

        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.43"));
    }

    @Test
    @DisplayName("WHIP 2.625 경계값 → HALF_UP → 2.63 (강재민 케이스)")
    void testWHIP_2점625_경계값_HALFUP반올림() {
        // (3+4)*3 / 8 = 21/8 = 2.625 → HALF_UP scale=2 → 2.63
        PitcherStats stats = PitcherStats.builder()
                .playerId(11L)
                .walksAllowed(3).hitsAllowed(4).inningsOuts(8)
                .build();

        BigDecimal result = sabermetricsService.calculateWHIP(stats);

        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("2.63"));
    }

    // ──────────────────────────────────────────────────────────────
    // 리그 평균 가중 계산 테스트 (K/9)
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("K/9 리그 평균 가중 계산: SO*27/outs")
    void getLeagueAverageKper9_가중평균_정확성() {
        // totalOuts=300, totalSO=100 → 100*27/300 = 2700/300 = 9.00
        LeagueTotalsDto dto = new LeagueTotalsDto();
        dto.setTotalOuts(300L);
        dto.setTotalStrikeouts(100L);
        dto.setTotalWalks(0L);
        dto.setTotalHomeRuns(0L);
        dto.setTotalHitsAllowed(0L);
        dto.setTotalEarnedRuns(0L);
        given(pitcherStatsMapper.findLeagueTotals()).willReturn(dto);

        BigDecimal result = sabermetricsService.getLeagueAverageKper9();

        assertThat(result).isNotNull();
        assertThat(result).isEqualByComparingTo(new BigDecimal("9.00"));
    }

    @Test
    @DisplayName("K/9 리그 평균 - 이닝 없으면 null 반환")
    void getLeagueAverageKper9_outs0_null반환() {
        LeagueTotalsDto dto = new LeagueTotalsDto();
        dto.setTotalOuts(0L);
        dto.setTotalStrikeouts(100L);
        dto.setTotalWalks(0L);
        dto.setTotalHomeRuns(0L);
        dto.setTotalHitsAllowed(0L);
        dto.setTotalEarnedRuns(0L);
        given(pitcherStatsMapper.findLeagueTotals()).willReturn(dto);

        BigDecimal result = sabermetricsService.getLeagueAverageKper9();

        assertThat(result).isNull();
    }

    // ──────────────────────────────────────────────────────────────
    // null 입력 방어 테스트
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("stats null이면 모든 계산 메서드 null 반환")
    void testNull_입력방어() {
        assertThat(sabermetricsService.calculateOBP(null, 100)).isNull();
        assertThat(sabermetricsService.calculateSLG(null)).isNull();
        assertThat(sabermetricsService.calculateOPS(null, 100)).isNull();
        assertThat(sabermetricsService.calculateKper9(null)).isNull();
        assertThat(sabermetricsService.calculateFIP(null, BigDecimal.ONE)).isNull();
    }
}
