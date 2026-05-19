package com.kbo.stats.external.kbo.dto.parsed;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ParsedGameEvent {
    String  eventType;        // HOME_RUN | TRIPLE | DOUBLE | ERROR | DOUBLE_PLAY | WILD_PITCH
    String  playerName;
    Integer inning;
    Integer seasonCount;      // 홈런 시즌 N호
    Integer runs;             // 홈런 점수 (솔로=1, 투런=2, ...)
    String  opponentPitcher;  // 홈런 상대 투수
    String  rawText;          // 원본 텍스트 (파싱 실패 추적용)
}
