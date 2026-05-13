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
