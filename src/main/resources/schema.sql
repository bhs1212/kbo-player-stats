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

-- 기존 테이블에 새 컬럼 추가 (이미 존재하면 무시)
ALTER TABLE player ADD COLUMN IF NOT EXISTS stolen_bases INT DEFAULT NULL COMMENT '도루' AFTER rbi;
ALTER TABLE player ADD COLUMN IF NOT EXISTS saves        INT DEFAULT NULL COMMENT '세이브' AFTER wins;
ALTER TABLE player ADD COLUMN IF NOT EXISTS holds        INT DEFAULT NULL COMMENT '홀드' AFTER saves;
