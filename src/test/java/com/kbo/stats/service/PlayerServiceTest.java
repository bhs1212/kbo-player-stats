package com.kbo.stats.service;

import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import com.kbo.stats.dto.PlayerFormDto;
import com.kbo.stats.dto.PlayerSearchDto;
import com.kbo.stats.mapper.PlayerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerMapper playerMapper;

    @InjectMocks
    private PlayerService playerService;

    private Player sampleBatter;

    @BeforeEach
    void setUp() {
        sampleBatter = Player.builder()
                .id(1L)
                .name("이정후")
                .team("키움 히어로즈")
                .position("외야수")
                .playerType(PlayerType.BATTER)
                .battingAvg(0.349)
                .homeRuns(23)
                .hits(183)
                .rbi(113)
                .games(144)
                .build();
    }

    @Test
    @DisplayName("선수 단건 조회 - 성공")
    void findById_success() {
        given(playerMapper.findById(1L)).willReturn(Optional.of(sampleBatter));

        Player result = playerService.findById(1L);

        assertThat(result.getName()).isEqualTo("이정후");
        assertThat(result.isBatter()).isTrue();
    }

    @Test
    @DisplayName("선수 단건 조회 - 없으면 예외")
    void findById_notFound() {
        given(playerMapper.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("선수를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("선수 등록")
    void save_player() {
        PlayerFormDto form = new PlayerFormDto();
        form.setName("박병호");
        form.setTeam("키움 히어로즈");
        form.setPosition("1루수");
        form.setPlayerType(PlayerType.BATTER);
        form.setHomeRuns(33);

        willDoNothing().given(playerMapper).insert(any(Player.class));

        playerService.save(form);

        then(playerMapper).should(times(1)).insert(any(Player.class));
    }

    @Test
    @DisplayName("홈런 랭킹 조회")
    void homeRunRanking() {
        given(playerMapper.findHomeRunRanking(10)).willReturn(List.of(sampleBatter));

        List<Player> ranking = playerService.getHomeRunRanking(10);

        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).getHomeRuns()).isEqualTo(23);
    }

    @Test
    @DisplayName("saveOrUpdate - 기존 선수 업데이트")
    void saveOrUpdate_existingPlayer() {
        given(playerMapper.findByNameAndTeam("이정후", "키움 히어로즈"))
                .willReturn(Optional.of(sampleBatter));
        willDoNothing().given(playerMapper).update(any(Player.class));

        playerService.saveOrUpdate(sampleBatter);

        then(playerMapper).should(never()).insert(any());
        then(playerMapper).should(times(1)).update(any());
    }
}
