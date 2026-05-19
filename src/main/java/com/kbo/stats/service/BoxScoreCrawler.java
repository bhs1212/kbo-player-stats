package com.kbo.stats.service;

import com.kbo.stats.domain.Game;
import com.kbo.stats.dto.BoxScoreCrawlSummary;
import com.kbo.stats.dto.BoxScoreCollectResult;
import com.kbo.stats.mapper.GameMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BoxScoreCrawler {

    private static final long SLEEP_MS = 500;

    private final GameMapper             gameMapper;
    private final BoxScoreCollectService collectService;

    /** 단일 날짜 박스스코어 크롤링 */
    public BoxScoreCrawlSummary crawlByDate(LocalDate date) {
        return crawlRange(date, date);
    }

    /**
     * 날짜 범위 내 완료된 경기 박스스코어 일괄 크롤링.
     * 한 게임 실패해도 전체 중단하지 않고 실패 목록만 기록.
     */
    public BoxScoreCrawlSummary crawlRange(LocalDate start, LocalDate end) {
        long startMs = System.currentTimeMillis();
        log.info("[박스스코어 크롤러] 시작 {} ~ {}", start, end);

        List<Game> games = gameMapper.findFinishedByDateRange(start, end);
        log.info("[박스스코어 크롤러] 대상 경기 {}건", games.size());

        int successCount = 0, skippedCount = 0, failedCount = 0;
        List<Long> failedGameIds = new ArrayList<>();

        for (Game game : games) {
            Long gameId = game.getId();
            try {
                BoxScoreCollectResult result = collectService.collectOne(gameId);
                switch (result.getStatus()) {
                    case SUCCESS -> successCount++;
                    case SKIPPED -> skippedCount++;
                    case FAILED  -> { failedCount++; failedGameIds.add(gameId); }
                }
                log.info("[박스스코어 크롤러] {} gameId={} kboGameId={} msg={}",
                        result.getStatus(), gameId, result.getKboGameId(), result.getMessage());

                if (result.getStatus() != BoxScoreCollectResult.Status.SKIPPED) {
                    Thread.sleep(SLEEP_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[박스스코어 크롤러] 인터럽트 발생, 크롤링 중단 (완료: {}건)", successCount);
                break;
            } catch (Exception e) {
                failedCount++;
                failedGameIds.add(gameId);
                log.error("[박스스코어 크롤러] 예외 gameId={}: {}", gameId, e.getMessage());
            }
        }

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("[박스스코어 크롤러] 완료 — 전체={} 성공={} 스킵={} 실패={} {}ms",
                games.size(), successCount, skippedCount, failedCount, durationMs);

        return BoxScoreCrawlSummary.builder()
                .totalCount(games.size())
                .successCount(successCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .failedGameIds(failedGameIds)
                .durationMs(durationMs)
                .build();
    }
}
