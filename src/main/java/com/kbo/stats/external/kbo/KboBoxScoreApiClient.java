package com.kbo.stats.external.kbo;

import com.kbo.stats.external.kbo.dto.KboBoxScoreResponse;
import com.kbo.stats.external.kbo.dto.KboScoreBoardResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class KboBoxScoreApiClient {

    private final WebClient webClient;
    private final String scoreBoardUrl;
    private final String boxScoreUrl;

    public KboBoxScoreApiClient(
            @Value("${kbo.api.scoreboard-url}") String scoreBoardUrl,
            @Value("${kbo.api.boxscore-url}")   String boxScoreUrl) {
        this.scoreBoardUrl = scoreBoardUrl;
        this.boxScoreUrl   = boxScoreUrl;
        this.webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    /**
     * KBO GetScoreBoardScroll 호출.
     *
     * @throws KboApiException 응답 code가 "100"이 아닐 때
     */
    public KboScoreBoardResponse fetchScoreBoard(int seasonId, String kboGameId) {
        log.info("[KBO] ScoreBoard 요청 url={} gameId={}", scoreBoardUrl, kboGameId);
        KboScoreBoardResponse response = webClient.post()
                .uri(scoreBoardUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("leId", "1")
                        .with("srId", "0")
                        .with("seasonId", String.valueOf(seasonId))
                        .with("gameId", kboGameId))
                .retrieve()
                .bodyToMono(KboScoreBoardResponse.class)
                .block();

        if (response == null) {
            throw new KboApiException("ScoreBoard 응답 null: gameId=" + kboGameId);
        }
        log.info("[KBO] ScoreBoard 응답 gameId={} code={} msg={}", kboGameId, response.getCode(), response.getMsg());
        if (!"100".equals(response.getCode())) {
            throw new KboApiException("ScoreBoard 실패 gameId=" + kboGameId
                    + " code=" + response.getCode() + " msg=" + response.getMsg());
        }
        return response;
    }

    /**
     * KBO GetBoxScoreScroll 호출.
     *
     * @throws KboApiException 응답 code가 "100"이 아닐 때
     */
    public KboBoxScoreResponse fetchBoxScore(int seasonId, String kboGameId) {
        log.info("[KBO] BoxScore 요청 url={} gameId={}", boxScoreUrl, kboGameId);
        KboBoxScoreResponse response = webClient.post()
                .uri(boxScoreUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("leId", "1")
                        .with("srId", "0")
                        .with("seasonId", String.valueOf(seasonId))
                        .with("gameId", kboGameId))
                .retrieve()
                .bodyToMono(KboBoxScoreResponse.class)
                .block();

        if (response == null) {
            throw new KboApiException("BoxScore 응답 null: gameId=" + kboGameId);
        }
        log.info("[KBO] BoxScore 응답 gameId={} code={} msg={}", kboGameId, response.getCode(), response.getMsg());
        if (!"100".equals(response.getCode())) {
            throw new KboApiException("BoxScore 실패 gameId=" + kboGameId
                    + " code=" + response.getCode() + " msg=" + response.getMsg());
        }
        return response;
    }
}
