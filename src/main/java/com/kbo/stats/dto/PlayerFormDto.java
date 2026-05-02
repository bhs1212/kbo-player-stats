package com.kbo.stats.dto;

import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlayerFormDto {

    private Long id;

    @NotBlank(message = "선수명을 입력하세요.")
    private String name;

    @NotBlank(message = "팀명을 입력하세요.")
    private String team;

    @NotBlank(message = "포지션을 입력하세요.")
    private String position;

    @NotNull(message = "선수 구분을 선택하세요.")
    private PlayerType playerType;

    // 타자 기록
    private Double battingAvg;
    private Integer homeRuns;
    private Integer hits;
    private Integer rbi;

    // 투수 기록
    private Double era;
    private Integer wins;

    private Integer games;

    public Player toEntity() {
        return Player.builder()
                .id(id)
                .name(name)
                .team(team)
                .position(position)
                .playerType(playerType)
                .battingAvg(battingAvg)
                .homeRuns(homeRuns)
                .hits(hits)
                .rbi(rbi)
                .era(era)
                .wins(wins)
                .games(games)
                .build();
    }

    public static PlayerFormDto from(Player player) {
        PlayerFormDto dto = new PlayerFormDto();
        dto.setId(player.getId());
        dto.setName(player.getName());
        dto.setTeam(player.getTeam());
        dto.setPosition(player.getPosition());
        dto.setPlayerType(player.getPlayerType());
        dto.setBattingAvg(player.getBattingAvg());
        dto.setHomeRuns(player.getHomeRuns());
        dto.setHits(player.getHits());
        dto.setRbi(player.getRbi());
        dto.setEra(player.getEra());
        dto.setWins(player.getWins());
        dto.setGames(player.getGames());
        return dto;
    }
}
