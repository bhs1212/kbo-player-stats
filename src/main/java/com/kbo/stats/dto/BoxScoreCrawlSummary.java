package com.kbo.stats.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BoxScoreCrawlSummary {
    int        totalCount;
    int        successCount;
    int        skippedCount;
    int        failedCount;
    List<Long> failedGameIds;
    long       durationMs;
}
