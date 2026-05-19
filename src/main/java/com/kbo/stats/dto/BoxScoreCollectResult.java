package com.kbo.stats.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BoxScoreCollectResult {

    public enum Status { SUCCESS, FAILED, SKIPPED }

    Status status;
    Long   gameId;
    String kboGameId;
    String message;

    public static BoxScoreCollectResult success(Long gameId, String kboGameId) {
        return BoxScoreCollectResult.builder()
                .status(Status.SUCCESS).gameId(gameId).kboGameId(kboGameId).message("OK").build();
    }

    public static BoxScoreCollectResult failed(Long gameId, String kboGameId, String message) {
        return BoxScoreCollectResult.builder()
                .status(Status.FAILED).gameId(gameId).kboGameId(kboGameId).message(message).build();
    }

    public static BoxScoreCollectResult skipped(Long gameId, String kboGameId, String reason) {
        return BoxScoreCollectResult.builder()
                .status(Status.SKIPPED).gameId(gameId).kboGameId(kboGameId).message(reason).build();
    }
}
