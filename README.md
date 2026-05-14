# ⚾ KBO 통계 시스템

KBO 리그의 선수 기록, 경기 일정/결과, AI 챗봇 기능을 제공하는 웹 애플리케이션입니다.

🌐 **데모 사이트**: [http://43.200.221.146:8080](http://43.200.221.146:8080)

> AWS EC2 (Ubuntu) + RDS MySQL로 배포

## 📌 프로젝트 소개

KBO 공식 사이트에서 선수 기록과 경기 일정을 자동으로 수집해 보여주고, 회원가입한 사용자는 응원팀을 설정해 개인화된 일정 화면을 받을 수 있습니다. Claude Haiku API 기반 챗봇으로 선수 기록을 자연어로 질문할 수 있고, 동일 질문 캐싱과 IP별 요청 제한으로 호출 비용을 관리합니다.

> 이 프로젝트는 **Claude Code(Anthropic의 AI 코딩 어시스턴트)와의 페어 프로그래밍**으로 개발했습니다. AI를 도구로 활용한 개발 방식과 그 과정에서의 의사결정은 [🤖 AI 협업 개발 방식](#-ai-협업-개발-방식) 섹션을 참고해주세요.

## 🛠 기술 스택

| 구분       | 기술                                      |
| ---------- | ----------------------------------------- |
| Backend    | Java 17, Spring Boot 3.x, Spring Security |
| DB         | MySQL 8.0, MyBatis                        |
| Crawling   | Selenium 4 (ChromeDriver), Jsoup          |
| AI         | Anthropic Claude API (Haiku)              |
| Cache      | Caffeine                                  |
| Rate Limit | Bucket4j                                  |
| Frontend   | Thymeleaf, Bootstrap 5                    |
| API Docs   | SpringDoc OpenAPI (Swagger UI)            |
| Build      | Maven                                     |
| Dev Tool   | Claude Code (AI Pair Programming)         |
| etc        | Lombok, BCrypt                            |

## 📁 프로젝트 구조

```
src/main/java/com/kbo/stats/
├── KboStatsApplication.java           # 메인 애플리케이션
├── config/
│   ├── SecurityConfig.java            # Spring Security 설정
│   ├── CacheConfig.java               # Caffeine 캐시 설정
│   ├── RateLimitConfig.java           # Bucket4j Rate Limit 설정
│   └── OpenApiConfig.java             # Swagger 설정
├── controller/
│   ├── HomeController.java            # 메인 페이지
│   ├── AuthController.java            # 회원가입, 로그인
│   ├── PlayerController.java          # 선수 CRUD
│   ├── GameController.java            # 경기 일정 페이지
│   ├── ChatbotController.java         # 챗봇
│   ├── GlobalExceptionHandler.java    # 전역 예외 처리
│   └── api/                           # REST API
├── service/
│   ├── UserService.java               # 회원가입, 응원팀 설정
│   ├── CustomUserDetailsService.java  # Spring Security 인증
│   ├── PlayerService.java
│   ├── PlayerCrawler.java             # 선수 데이터 크롤링
│   ├── GameService.java
│   ├── GameCrawler.java               # 경기 일정 Selenium 크롤링
│   └── ChatbotService.java            # Claude API 호출 (RAG)
├── mapper/
│   ├── UserMapper.java
│   ├── PlayerMapper.java
│   └── GameMapper.java
├── domain/
│   ├── User.java
│   ├── Player.java
│   ├── Game.java
│   └── GameStatus.java
└── dto/
    ├── SignUpDto.java
    └── ChatMessageDto.java
```

## 🗄 ERD

```
+------------------+      +------------------+      +------------------+
|   user_account   |      |      player      |      |       game       |
+------------------+      +------------------+      +------------------+
| id (PK)          |      | id (PK)          |      | id (PK)          |
| username (UNI)   |      | name             |      | game_date        |
| password         |      | team             |      | game_time        |
| favorite_team    |      | position         |      | away_team        |
| role             |      | back_number      |      | home_team        |
| created_at       |      | batting_avg      |      | away_score       |
+------------------+      | obp / slg        |      | home_score       |
                          | era              |      | status           |
                          | wins / losses    |      | stadium          |
                          | last_updated     |      | UNIQUE(date,     |
                          +------------------+      |   away, home)    |
                                                    +------------------+
```

### 테이블 설명

- **user_account**: 사용자 계정 (role: USER / ADMIN, favorite_team으로 응원팀 저장)
- **player**: 선수 기록 (KBO 공식 사이트에서 자동 수집)
- **game**: 경기 일정/결과 (status: SCHEDULED, FINISHED, POSTPONED, CANCELED 등)

## ✨ 주요 기능

### 인증/권한

- Spring Security 기반 로그인/로그아웃 (CSRF 보호 적용)
- BCrypt 비밀번호 암호화
- ADMIN / USER / 익명 권한 분리
- 회원가입 시 응원팀 설정 (10개 구단 중 선택)
- 권한 부족 시 홈 리다이렉트 + 안내 메시지

### 선수 기록

- KBO 공식 사이트에서 타자/투수 기록 자동 수집
- 팀별/포지션별 랭킹 조회
- 통계 시각화 차트
- 매일 새벽 자동 갱신 스케줄러

### 경기 일정/결과

- KBO 공식 사이트 Selenium 크롤링 (시즌 전체 약 685건 수집)
- 드롭다운 폼 제출 구조 대응을 위한 Selenium `Select` 활용
- 월별 드롭다운으로 일정 조회
- 응원팀 자동 필터 (로그인 시 응원팀 경기 우선)
- 팀 대표색 표시 + 승패 강조 (승리팀 굵게, 패배팀 회색)
- 시점별 seriesId 동적 분기 (10월 이후 포스트시즌 자동 포함)
- 매일 새벽 4시 / 오후 11시 자동 갱신 스케줄러

### AI 챗봇

- Anthropic Claude Haiku API 사용
- RAG 방식 - DB의 실제 선수 데이터를 컨텍스트로 전달해 환각(hallucination) 방지
- Caffeine 캐싱 - 동일 질문은 LLM 호출 없이 캐시 응답 (약 3초 → 0.1초 미만)
- Bucket4j Rate Limiting - IP별 분당 10회 / 일 500회 제한

## 💡 트러블슈팅

### 1. 크롤링 속도 21초 → 13초 (39% 단축)

처음 만든 Selenium 크롤러가 페이지 하나에 21초가 걸렸습니다. 매일 자동으로 도는 작업이라 시간을 줄일 필요가 있었습니다.

원인 분석 결과 Selenium이 페이지의 이미지, CSS, 폰트까지 전부 다운로드하고 있었는데, 실제 필요한 것은 HTML 텍스트뿐이었습니다. Chrome 옵션으로 이미지와 CSS 로드를 비활성화하고, 페이지 소스를 받아 Jsoup으로 파싱하도록 변경했습니다.

결과 13초로 단축. 측정 → 분석 → 개선 → 재측정 사이클을 적용했습니다.

### 2. ASP.NET 사이트의 동적 페이지 크롤링

KBO 일정 페이지는 ASP.NET 기반으로 URL의 `gameMonth` 파라미터를 무시하고, 페이지 내 드롭다운(Form Submit)으로만 월 변경이 가능한 구조였습니다.

네이버 스포츠로 우회를 시도했지만 React SPA라 CSS 클래스명이 빌드마다 해시값으로 바뀌어 안정성이 낮았습니다.

최종적으로 Selenium의 `Select` 클래스로 드롭다운을 직접 조작하는 방식으로 해결했습니다. ChromeDriver 인스턴스를 한 번만 생성하고 드롭다운 값만 9번 변경하면서 3월~9월 전체를 30~40초에 수집하도록 최적화했습니다.

```java
WebElement dropdown = wait.until(
    ExpectedConditions.elementToBeClickable(
        By.cssSelector("select[id*=Month]")));
Select select = new Select(dropdown);
select.selectByValue(String.format("%02d", month));
```

### 3. 포스트시즌 placeholder 데이터 처리

KBO 사이트의 `seriesId=0,9,6` 파라미터로 정규시즌+포스트시즌 데이터를 함께 가져오려 했더니, 시즌 초반에 호출 시 10월/11월에 등록된 잠정 매치업이 들어왔습니다. 동일한 매치업 5개가 두 날짜에 그대로 복사되어 저장됐습니다.

`seriesId=0`(정규시즌만)으로 변경하면 가짜 데이터는 막을 수 있지만 실제 포스트시즌이 등록되어도 가져오지 못하는 문제가 있었습니다.

현재 날짜를 기준으로 시리즈 ID를 동적으로 결정하는 방식으로 해결했습니다. 3월~9월에는 정규시즌만, 10월 이후에는 포스트시즌 시리즈도 함께 호출합니다. 코드 수정 없이 시점이 되면 자동으로 가을야구 일정이 채워집니다.

```java
private String resolveSeriesIds(int year, int month) {
    LocalDate now = LocalDate.now();
    if (month <= 9) return "0";  // 정규시즌만
    return (now.getMonthValue() >= 10) ? "0,9,6" : "0";
}
```

### 4. AI 챗봇 비용 및 남용 방지

Claude API는 호출당 과금이라 동일 질문 반복이나 악의적 다량 호출 시 비용이 누적됩니다.

세 가지 방식을 함께 적용했습니다.

- **Caffeine 캐싱**: 동일 질문은 LLM 호출 없이 캐시에서 반환. 응답 시간이 약 3초 → 0.1초 미만으로 단축
- **Bucket4j Rate Limiting**: 토큰 버킷 알고리즘으로 IP별 분당 10회 / 일 500회 제한
- **RAG 패턴**: 1단계로 사용자 의도 파악 후 DB에서 실제 데이터 조회 → 그 데이터를 컨텍스트로 LLM에 전달. LLM이 추측으로 답하지 못하게 차단

## 🤖 AI 협업 개발 방식

이 프로젝트는 **Claude Code**(Anthropic의 AI 코딩 어시스턴트)와의 페어 프로그래밍으로 개발했습니다. 단순히 코드를 생성받아 붙여넣는 방식이 아니라, AI를 협업 파트너로 두고 설계 → 구현 → 디버깅 사이클을 함께 진행했습니다.

### 활용 방식

- **기능 설계 단계**: 요구사항을 정리해 Claude Code에 전달하고 초기 구현 생성. 그대로 사용하지 않고 프로젝트 컨벤션과 기존 코드 패턴에 맞게 검토·수정
- **디버깅 단계**: 콘솔 로그와 에러 메시지를 직접 분석한 뒤 가설을 세워 Claude Code와 함께 검증. AI가 놓친 부분은 직접 사이트 구조 분석이나 SQL 쿼리로 보완
- **의사결정 단계**: Claude Code가 제안한 해결책 중 trade-off를 비교해 선택. 예: 자동화 범위, 라이브러리 도입 여부, 휴리스틱 vs 명시적 분기 등

### AI에게 맡기지 않은 영역

- **외부 시스템 구조 분석**: 네이버 스포츠 SPA의 클래스명, KBO 사이트의 ASP.NET 드롭다운 동작 등은 브라우저 개발자 도구로 직접 확인
- **데이터 검증**: SQL 쿼리로 실제 저장된 데이터의 이상 징후를 확인하고 원인 추적
- **요구사항 정의와 우선순위**: 어떤 기능을 어디까지 만들지, 어떤 trade-off를 받아들일지

### 협업으로 해결한 대표 사례

- 네이버 스포츠의 React 빌드 해시 클래스를 직접 분석해 정확한 selector를 전달, AI가 잡지 못하던 매칭 문제 해결
- KBO 사이트가 URL 파라미터를 무시한다는 사실을 직접 확인하고 Selenium 드롭다운 조작 방향을 제안
- 10월/11월에 들어온 placeholder 데이터를 SQL로 진단하고 시점 기반 동적 분기 로직 도출

### 배운 점

- AI가 만드는 코드의 정확성은 입력 컨텍스트의 질에 비례한다는 것
- 디버깅의 핵심인 가설 수립과 원인 추적은 사람이 직접 해야 한다는 것
- AI를 코드 생성기로 보기보다 페어 프로그래밍 파트너로 활용할 때 효과가 크다는 것

## 📸 화면 미리보기

### 메인 화면

![메인](images/main.png)

### 로그인 / 회원가입

![로그인](images/login.png)

### 경기 일정/결과

![경기 일정](images/games.png)

### 선수 랭킹

![선수 랭킹](images/ranking.png)

### AI 챗봇

![챗봇](images/chatbot.png)

## 🚀 실행 방법

### 1. 사전 요구사항

- Java 17
- MySQL 8.0
- Maven 3.8 이상
- Chrome 브라우저 (Selenium 크롤링용)

### 2. DB 설정

```sql
CREATE DATABASE kbo_stats DEFAULT CHARACTER SET utf8mb4;
USE kbo_stats;

CREATE TABLE user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    favorite_team VARCHAR(20),
    role VARCHAR(20) DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE game (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_date DATE NOT NULL,
    game_time TIME,
    away_team VARCHAR(20) NOT NULL,
    home_team VARCHAR(20) NOT NULL,
    away_score INT,
    home_score INT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    stadium VARCHAR(50),
    notes VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game (game_date, away_team, home_team),
    INDEX idx_game_date (game_date),
    INDEX idx_game_status (status)
);

-- player 테이블은 schema.sql 참고
```

### 3. 환경변수 설정

`src/main/resources/application-local.yml` 파일을 생성하고 아래 값을 입력하세요:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/kbo_stats?serverTimezone=Asia/Seoul
    username: your_username
    password: your_password

anthropic:
  api-key: YOUR_CLAUDE_API_KEY

admin:
  username: admin
  password: YOUR_ADMIN_PASSWORD
```

> ⚠️ 이 파일은 `.gitignore`에 등록되어 있어 Git에 올라가지 않습니다.

### 4. 실행

```bash
./mvnw spring-boot:run
```

브라우저에서 `http://localhost:8080` 접속
API 문서는 `http://localhost:8080/swagger-ui.html`에서 확인할 수 있습니다.

## 📡 REST API

| Method | URL                     | 설명             | 권한  |
| ------ | ----------------------- | ---------------- | ----- |
| GET    | /api/v1/players         | 선수 목록 조회   | 모두  |
| GET    | /api/v1/players/{id}    | 선수 상세 조회   | 모두  |
| POST   | /api/v1/players         | 선수 등록        | ADMIN |
| PUT    | /api/v1/players/{id}    | 선수 수정        | ADMIN |
| DELETE | /api/v1/players/{id}    | 선수 삭제        | ADMIN |
| GET    | /api/v1/games?from=&to= | 경기 일정 조회   | 모두  |
| GET    | /api/v1/games/{id}      | 경기 상세 조회   | 모두  |
| POST   | /games/crawl            | 경기 데이터 갱신 | ADMIN |
| POST   | /players/crawl          | 선수 데이터 갱신 | ADMIN |
| POST   | /api/v1/chatbot         | 챗봇 질의        | 모두  |
| POST   | /signup                 | 회원가입         | 익명  |
| GET    | /my/profile             | 마이페이지       | USER  |
| POST   | /my/team                | 응원팀 변경      | USER  |
