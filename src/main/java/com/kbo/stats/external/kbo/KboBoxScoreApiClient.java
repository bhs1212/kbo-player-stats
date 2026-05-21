package com.kbo.stats.external.kbo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbo.stats.external.kbo.dto.KboBoxScoreResponse;
import com.kbo.stats.external.kbo.dto.KboScoreBoardResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class KboBoxScoreApiClient {

    private final WebClient webClient;
    private final String scoreBoardUrl;
    private final String boxScoreUrl;

    public KboBoxScoreApiClient(
            @Value("${kbo.api.scoreboard-url}") String scoreBoardUrl,
            @Value("${kbo.api.boxscore-url}") String boxScoreUrl,
            ObjectMapper objectMapper) {
        this.scoreBoardUrl = scoreBoardUrl;
        this.boxScoreUrl = boxScoreUrl;

        // KBO는 JSON 본문을 Content-Type: text/plain 으로 반환 → text/plain 도 JSON 디코더에 매핑
        Jackson2JsonDecoder jsonDecoder = new Jackson2JsonDecoder(
                objectMapper,
                MediaType.APPLICATION_JSON,
                MediaType.TEXT_PLAIN);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> {
                    config.defaultCodecs().maxInMemorySize(4 * 1024 * 1024);
                    config.defaultCodecs().jackson2JsonDecoder(jsonDecoder);
                })
                .build();

        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                // KBO .asmx 봇 탐지 우회용 헤더
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Referer", "https://www.koreabaseball.com/Schedule/ScoreBoard.aspx")
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
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