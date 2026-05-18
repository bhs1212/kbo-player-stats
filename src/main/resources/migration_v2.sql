-- ============================================================
-- KBO Stats v2 마이그레이션
-- 목적: 세이버메트릭스 계산을 위한 원시 데이터 + 사이트 계산값 저장
-- 전략: 기존 player 테이블 유지 + 분리 테이블 2개 신규
-- ============================================================

USE kbo_stats;

-- ──────────────────────────────────────────────────────────────
-- 1. 타자 상세 기록 테이블
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS player_batter_stats (
    player_id           BIGINT PRIMARY KEY      COMMENT 'player.id 참조 (1:1)',

    -- ── 원시 데이터 (KBO 사이트 Basic1 + Basic2 페이지) ──
    plate_appearances   INT      DEFAULT NULL   COMMENT 'PA, 타석',
    at_bats             INT      DEFAULT NULL   COMMENT 'AB, 타수',
    runs                INT      DEFAULT NULL   COMMENT 'R, 득점',
    doubles             INT      DEFAULT NULL   COMMENT '2B, 2루타',
    triples             INT      DEFAULT NULL   COMMENT '3B, 3루타',
    total_bases         INT      DEFAULT NULL   COMMENT 'TB, 루타 합계',
    walks               INT      DEFAULT NULL   COMMENT 'BB, 볼넷',
    intentional_walks   INT      DEFAULT NULL   COMMENT 'IBB, 고의사구',
    hit_by_pitch        INT      DEFAULT NULL   COMMENT 'HBP, 사구',
    strikeouts          INT      DEFAULT NULL   COMMENT 'SO, 삼진',
    double_plays        INT      DEFAULT NULL   COMMENT 'GDP, 병살타',
    sacrifice_hits      INT      DEFAULT NULL   COMMENT 'SAC, 희생번트',
    sacrifice_flies     INT      DEFAULT NULL   COMMENT 'SF, 희생플라이',

    -- ── 사이트 계산값 (검증용으로 저장) ──
    slugging_pct        DECIMAL(5, 3) DEFAULT NULL  COMMENT 'SLG, 장타율 (사이트값)',
    on_base_pct         DECIMAL(5, 3) DEFAULT NULL  COMMENT 'OBP, 출루율 (사이트값)',
    ops                 DECIMAL(5, 3) DEFAULT NULL  COMMENT 'OPS (사이트값)',

    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_batter_player
        FOREIGN KEY (player_id) REFERENCES player(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='타자 상세 기록 (세이버메트릭스용)';


-- ──────────────────────────────────────────────────────────────
-- 2. 투수 상세 기록 테이블
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS player_pitcher_stats (
    player_id           BIGINT PRIMARY KEY      COMMENT 'player.id 참조 (1:1)',

    -- ── 원시 데이터 (Basic1 + Basic2) ──
    losses              INT      DEFAULT NULL   COMMENT 'L, 패',
    win_pct             DECIMAL(5, 3) DEFAULT NULL COMMENT 'WPCT, 승률',
    innings_outs        INT      DEFAULT NULL   COMMENT 'IP를 outs로 환산 (52⅓이닝 = 157 outs)',
    hits_allowed        INT      DEFAULT NULL   COMMENT 'H, 피안타',
    home_runs_allowed   INT      DEFAULT NULL   COMMENT 'HR, 피홈런',
    walks_allowed       INT      DEFAULT NULL   COMMENT 'BB, 볼넷 허용',
    hbp_allowed         INT      DEFAULT NULL   COMMENT 'HBP, 사구 허용',
    strikeouts          INT      DEFAULT NULL   COMMENT 'SO, 탈삼진',
    runs_allowed        INT      DEFAULT NULL   COMMENT 'R, 실점',
    earned_runs         INT      DEFAULT NULL   COMMENT 'ER, 자책점',

    -- ── 사이트 계산값 ──
    whip                DECIMAL(5, 2) DEFAULT NULL  COMMENT 'WHIP (사이트값)',

    -- ── Basic2 보조 지표 ──
    complete_games      INT      DEFAULT NULL   COMMENT 'CG, 완투',
    shutouts            INT      DEFAULT NULL   COMMENT 'SHO, 완봉',
    quality_starts      INT      DEFAULT NULL   COMMENT 'QS, 퀄리티스타트',
    blown_saves         INT      DEFAULT NULL   COMMENT 'BSV, 블론세이브',
    batters_faced       INT      DEFAULT NULL   COMMENT 'TBF, 상대 타자수',
    pitches_thrown      INT      DEFAULT NULL   COMMENT 'NP, 투구수',
    opponent_avg        DECIMAL(5, 3) DEFAULT NULL  COMMENT '피안타율',
    wild_pitches        INT      DEFAULT NULL   COMMENT 'WP, 폭투',
    balks               INT      DEFAULT NULL   COMMENT 'BK, 보크',

    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_pitcher_player
        FOREIGN KEY (player_id) REFERENCES player(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='투수 상세 기록 (세이버메트릭스용)';


-- ──────────────────────────────────────────────────────────────
-- 3. 검증 결과 로그 테이블 (선택사항이지만 어필 포인트)
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stat_validation_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_id       BIGINT      NOT NULL,
    metric_name     VARCHAR(20) NOT NULL    COMMENT '검증 지표명 (OPS/WHIP 등)',
    site_value      DECIMAL(8, 4)           COMMENT 'KBO 사이트 값',
    calculated_value DECIMAL(8, 4)          COMMENT '직접 계산 값',
    diff            DECIMAL(8, 4)           COMMENT '차이 (절댓값)',
    is_match        BOOLEAN     NOT NULL    COMMENT '0.001 이내 일치 여부',
    crawled_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_validation_player (player_id),
    INDEX idx_validation_metric (metric_name),
    INDEX idx_validation_match  (is_match)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='크롤링 데이터 검증 로그';
