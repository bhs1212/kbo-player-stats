CREATE DATABASE IF NOT EXISTS kbo_stats
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE kbo_stats;

CREATE TABLE IF NOT EXISTS player (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)    NOT NULL COMMENT '선수명',
    team        VARCHAR(50)    NOT NULL COMMENT '소속팀',
    position    VARCHAR(30)    NOT NULL COMMENT '포지션',
    player_type VARCHAR(10)    NOT NULL COMMENT '타자/투수 구분 (BATTER/PITCHER)',

    -- 타자 기록
    batting_avg  DECIMAL(5, 3) DEFAULT NULL COMMENT '타율',
    home_runs    INT           DEFAULT NULL COMMENT '홈런',
    hits         INT           DEFAULT NULL COMMENT '안타',
    rbi          INT           DEFAULT NULL COMMENT '타점',
    stolen_bases INT           DEFAULT NULL COMMENT '도루',

    -- 투수 기록
    era          DECIMAL(5, 2) DEFAULT NULL COMMENT '방어율',
    wins         INT           DEFAULT NULL COMMENT '승',
    saves        INT           DEFAULT NULL COMMENT '세이브',
    holds        INT           DEFAULT NULL COMMENT '홀드',

    -- 공통
    games       INT            DEFAULT NULL COMMENT '경기수',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    INDEX idx_player_team (team),
    INDEX idx_player_type (player_type),
    INDEX idx_player_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KBO 선수 기록';


-- 일반 사용자 계정 테이블 (MySQL 예약어 충돌 방지를 위해 user_account로 명명)
CREATE TABLE IF NOT EXISTS user_account (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(20)  NOT NULL UNIQUE COMMENT '사용자 아이디',
    password      VARCHAR(255) NOT NULL         COMMENT 'BCrypt 해시된 비밀번호',
    favorite_team VARCHAR(20)  NOT NULL         COMMENT '응원 팀',
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '권한(USER/ADMIN)',
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '가입일'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='일반 회원 계정';


-- 경기 일정/결과 테이블
CREATE TABLE IF NOT EXISTS game (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    game_date   DATE         NOT NULL         COMMENT '경기 날짜',
    game_time   TIME                          COMMENT '경기 시작 시간',
    away_team   VARCHAR(20)  NOT NULL         COMMENT '원정팀',
    home_team   VARCHAR(20)  NOT NULL         COMMENT '홈팀',
    away_score  INT                           COMMENT '원정팀 점수',
    home_score  INT                           COMMENT '홈팀 점수',
    status      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED'
                                             COMMENT '상태 (SCHEDULED/IN_PROGRESS/FINISHED/POSTPONED/CANCELED)',
    stadium     VARCHAR(50)                   COMMENT '경기장',
    notes       VARCHAR(200)                  COMMENT '비고 (우천 취소 사유 등)',
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP         COMMENT '등록일',
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    UNIQUE KEY uk_game (game_date, away_team, home_team),
    INDEX idx_game_date   (game_date),
    INDEX idx_game_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KBO 경기 일정 및 결과';


-- ============================================================
-- v3: KBO 박스스코어 도메인 스키마
-- ============================================================

-- game 테이블에 KBO gameId 컬럼 추가
ALTER TABLE game
    ADD COLUMN IF NOT EXISTS kbo_game_id VARCHAR(20) UNIQUE NULL
        COMMENT 'KBO gameId 예: 20260517LTOB0'
        AFTER notes;

-- 2-1. game_boxscore (게임 박스스코어 메타, game과 1:1)
CREATE TABLE IF NOT EXISTS game_boxscore (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id          BIGINT      NOT NULL UNIQUE,
    le_id            INT         NOT NULL DEFAULT 1    COMMENT '1=1군, 2=퓨처스',
    sr_id            INT         NOT NULL DEFAULT 0    COMMENT '0=정규시즌',
    season_id        INT         NOT NULL,
    crowd_cn         INT         NULL                  COMMENT '관중수',
    start_tm         VARCHAR(10) NULL                  COMMENT '경기 시작 HH:mm',
    end_tm           VARCHAR(10) NULL                  COMMENT '경기 종료 HH:mm',
    use_tm           VARCHAR(10) NULL                  COMMENT '경기 시간 H:mm',
    max_inning       INT         NULL,
    real_max_inning  INT         NULL,
    home_season_w    INT         NULL,
    home_season_l    INT         NULL,
    home_season_d    INT         NULL,
    away_season_w    INT         NULL,
    away_season_l    INT         NULL,
    away_season_d    INT         NULL,
    home_hits        INT         NULL,
    home_errors      INT         NULL,
    home_walks       INT         NULL,
    away_hits        INT         NULL,
    away_errors      INT         NULL,
    away_walks       INT         NULL,
    finishing_hit    VARCHAR(100) NULL                 COMMENT '결승타 (없음 또는 선수명)',
    umpires          VARCHAR(200) NULL                 COMMENT '심판 4명 콤마 구분',
    created_at       DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_boxscore_game FOREIGN KEY (game_id) REFERENCES game(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게임 박스스코어 메타';

-- 2-2. game_inning_score (이닝별 득점)
CREATE TABLE IF NOT EXISTS game_inning_score (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id    BIGINT               NOT NULL,
    inning     INT                  NOT NULL COMMENT '1~12',
    team_side  ENUM('AWAY','HOME')  NOT NULL,
    score      INT                  NULL     COMMENT 'NULL=미진행/연장 미사용',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inning (game_id, team_side, inning),
    CONSTRAINT fk_inning_game FOREIGN KEY (game_id) REFERENCES game(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='이닝별 득점';

-- 2-3. game_event (홈런/2루타/3루타/실책/병살타/폭투 등 주요 이벤트)
CREATE TABLE IF NOT EXISTS game_event (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id          BIGINT NOT NULL,
    event_type       ENUM('HOME_RUN','TRIPLE','DOUBLE','ERROR','DOUBLE_PLAY','WILD_PITCH') NOT NULL,
    player_name      VARCHAR(50)  NULL,
    inning           INT          NULL,
    season_count     INT          NULL     COMMENT '홈런 시즌 N호',
    runs             INT          NULL     COMMENT '홈런 N점 (솔로=1, 투런=2 ...)',
    opponent_pitcher VARCHAR(50)  NULL     COMMENT '홈런 상대 투수',
    raw_text         VARCHAR(200) NOT NULL COMMENT '원본 텍스트 백업',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_game (game_id, event_type),
    CONSTRAINT fk_event_game FOREIGN KEY (game_id) REFERENCES game(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='경기 주요 이벤트';

-- 2-4. game_batter_log (게임당 타자 기록, 양팀 합쳐 평균 18~26명)
CREATE TABLE IF NOT EXISTS game_batter_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id        BIGINT               NOT NULL,
    team_side      ENUM('AWAY','HOME')  NOT NULL,
    batting_order  INT                  NOT NULL COMMENT '타순 1~9',
    position       VARCHAR(10)          NULL     COMMENT '중/좌/우/一/二/三/유/포/지/타',
    player_name    VARCHAR(50)          NOT NULL,
    at_bats        INT DEFAULT 0,
    hits           INT DEFAULT 0,
    rbi            INT DEFAULT 0,
    walks          INT DEFAULT 0,
    season_avg     DECIMAL(4,3)         NULL     COMMENT '시즌 누적 타율 (보너스)',
    inning_results JSON                 NULL     COMMENT '이닝별 타격결과 ["좌비",null,"좌비",...]',
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_batter_game (game_id, team_side, batting_order),
    CONSTRAINT fk_batter_game FOREIGN KEY (game_id) REFERENCES game(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게임당 타자 기록';

-- 2-5. game_pitcher_log (게임당 투수 기록)
CREATE TABLE IF NOT EXISTS game_pitcher_log (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id              BIGINT               NOT NULL,
    team_side            ENUM('AWAY','HOME')  NOT NULL,
    pitch_order          INT                  NOT NULL COMMENT '등판 순서 1=선발',
    player_name          VARCHAR(50)          NOT NULL,
    appearance_label     VARCHAR(20)          NULL     COMMENT '"선발" or "7.9"(7회 9번타자 때 등판)',
    result               ENUM('WIN','LOSE','SAVE','HOLD','NONE') DEFAULT 'NONE',
    season_w             INT DEFAULT 0,
    season_l             INT DEFAULT 0,
    season_s             INT DEFAULT 0,
    innings_pitched_outs INT                  NOT NULL DEFAULT 0 COMMENT '아웃 카운트 정수 (6.2이닝 = 20)',
    batters_faced        INT DEFAULT 0,
    pitches              INT DEFAULT 0,
    at_bats_against      INT DEFAULT 0,
    hits_against         INT DEFAULT 0,
    home_runs_against    INT DEFAULT 0,
    walks_hbp            INT DEFAULT 0,
    strikeouts           INT DEFAULT 0,
    runs_allowed         INT DEFAULT 0,
    earned_runs          INT DEFAULT 0,
    season_era           DECIMAL(5,2)         NULL     COMMENT '시즌 누적 ERA (보너스)',
    created_at           DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pitcher_game (game_id, team_side, pitch_order),
    CONSTRAINT fk_pitcher_game FOREIGN KEY (game_id) REFERENCES game(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게임당 투수 기록';
