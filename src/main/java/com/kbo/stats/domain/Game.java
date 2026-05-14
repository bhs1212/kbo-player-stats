package com.kbo.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Game {

    private Long id;
    private LocalDate gameDate;
    private LocalTime gameTime;
    private String awayTeam;
    private String homeTeam;
    private Integer awayScore;
    private Integer homeScore;
    private GameStatus status;
    private String stadium;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isHomeWin() {
        return status == GameStatus.FINISHED
                && homeScore != null && awayScore != null
                && homeScore > awayScore;
    }

    public boolean isAwayWin() {
        return status == GameStatus.FINISHED
                && homeScore != null && awayScore != null
                && awayScore > homeScore;
    }

    public boolean isDraw() {
        return status == GameStatus.FINISHED
                && homeScore != null && awayScore != null
                && homeScore.equals(awayScore);
    }

    /** FINISHED 상태일 때 "원정점수 - 홈점수" 형식 반환 */
    public String getResultText() {
        if (status != GameStatus.FINISHED || homeScore == null || awayScore == null) {
            return "vs";
        }
        return awayScore + " : " + homeScore;
    }

    /** 팀명 → CSS 색상 클래스 (내부 전용) */
    private String teamColorClass(String team) {
        if (team == null) return "";
        return switch (team) {
            case "KIA"  -> "team-kia";
            case "두산"  -> "team-doosan";
            case "LG"   -> "team-lg";
            case "SSG"  -> "team-ssg";
            case "삼성"  -> "team-samsung";
            case "KT"   -> "team-kt";
            case "롯데"  -> "team-lotte";
            case "한화"  -> "team-hanwha";
            case "NC"   -> "team-nc";
            case "키움"  -> "team-kiwoom";
            default     -> "";
        };
    }

    /** 원정팀 표시용 CSS 클래스 (팀 색상 + 승/패 강조) */
    public String getAwayTeamClass() {
        String color = teamColorClass(awayTeam);
        String base  = color.isEmpty() ? "team-away" : "team-away " + color;
        if (status != GameStatus.FINISHED || awayScore == null || homeScore == null) return base;
        if (awayScore > homeScore) return base + " team-winner";
        if (awayScore < homeScore) return base + " team-loser";
        return base;
    }

    /** 홈팀 표시용 CSS 클래스 (팀 색상 + 홈 강조 + 승/패 강조) */
    public String getHomeTeamClass() {
        String color = teamColorClass(homeTeam);
        String base  = color.isEmpty() ? "team-home" : "team-home " + color;
        if (status != GameStatus.FINISHED || awayScore == null || homeScore == null) return base;
        if (homeScore > awayScore) return base + " team-winner";
        if (homeScore < awayScore) return base + " team-loser";
        return base;
    }
}
