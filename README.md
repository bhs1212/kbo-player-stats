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

### 박스스코어 도메인 (game 테이블에 1:N 관계, ON DELETE CASCADE)

- **game_boxscore**: 경기 메타 (관중수, 시작/종료 시간, 심판, 결승타)
- **game_inning_score**: 이닝별 점수 (경기 × 이닝 × 팀)
- **game_event**: 주요 이벤트 (홈런, 결승타, 심판 판정)
- **game_batter_log**: 타자 출장 기록 (타순, 포지션, 타수, 안타, 타점, 시즌 평균)
- **game_pitcher_log**: 투수 등판 기록 (등판 순서, 이닝 outs, 자책점, 시즌 ERA)

이닝은 outs 정수로 저장 (5⅔이닝 = 17 outs). BigDecimal 반올림 경계값 문제 회피.

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

### 경기 박스스코어

- KBO 박스스코어 API에서 경기별 상세 데이터 자동 수집 (이닝 점수, 주요 이벤트, 타자/투수 출장 기록)
- 5개 정규화 테이블 (게임 메타, 이닝, 이벤트, 타자 로그, 투수 로그) - 1:N + ON DELETE CASCADE
- `INSERT ON DUPLICATE KEY UPDATE` + `deleteByGameId`로 멱등 ETL
- Spring Cache(`@Cacheable`) + `@CacheEvict`로 GET 최적화 및 데이터 변경 시 자동 무효화
- 단일 경기 + 날짜별 일괄 수집 컨트롤러, 500ms 간격 sleep
- 12회 연장, 끝내기 경기 등 엣지 케이스 처리
- 시즌 전체 216경기 박스스코어 적재 + KBO 공식 사이트와 1:1 정합성 검증
- 매일 23:50 자동 수집 스케줄러 (`@Scheduled`)

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

### 5. KBO 박스스코어 API 디버깅 — 4단계 함정 추적

박스스코어 데이터 수집을 위해 KBO의 `.asmx` 엔드포인트를 호출했는데 처음엔 응답이 계속 엉뚱하게 왔습니다. 단계별로 좁혀가야 했습니다.

처음엔 HTTP 200을 받았는데 사용자 화면에선 권한 거부 페이지로 리다이렉트됐습니다. Spring Security의 CSRF 토큰 누락 때문에 `InvalidCsrfTokenException`이 발생했고, 이게 `AccessDeniedException`의 하위 예외라 권한 거부로 분기된 거였습니다. SecurityConfig에서 해당 endpoint를 CSRF 예외에 등록해서 해결.

다음엔 응답이 KBO가 아닌 다른 사이트에서 오는 걸 알아차렸습니다. 환경변수 fallback default 값에 잘못된 URL이 박혀 있었습니다. `application.yml`을 KBO 공식 endpoint로 바꿔 해결.

세 번째로 응답이 `Content-Type: text/html`에 KBO 에러 페이지가 들어왔습니다. KBO 사이트가 `.asmx` AJAX 엔드포인트에 `Referer`/`User-Agent`/`X-Requested-With` 헤더 검증을 걸어둔 거였습니다. WebClient에 브라우저 헤더를 `defaultHeader`로 추가하니 정상 JSON이 응답됐습니다.

마지막으로 응답이 JSON인데 Content-Type이 `text/plain`으로 와서 Spring WebClient의 Jackson 디코더가 매칭 못 하는 문제. `ExchangeStrategies`로 Jackson2JsonDecoder의 미디어 타입에 `text/plain`을 추가해서 해결.

각 단계마다 응답 메시지의 패턴(200 OK + redirect, `baseUrl` 합성 형태, 에러 페이지의 `<title>` 태그)을 단서로 역추적했습니다.

```java
Jackson2JsonDecoder jsonDecoder = new Jackson2JsonDecoder(
        objectMapper,
        MediaType.APPLICATION_JSON,
        MediaType.TEXT_PLAIN);  // KBO API는 JSON을 text/plain으로 응답

this.webClient = WebClient.builder()
    .exchangeStrategies(ExchangeStrategies.builder()
        .codecs(c -> c.defaultCodecs().jackson2JsonDecoder(jsonDecoder))
        .build())
    .defaultHeader("User-Agent", "Mozilla/5.0 ...")
    .defaultHeader("Referer", "https://www.koreabaseball.com/Schedule/ScoreBoard.aspx")
    .defaultHeader("X-Requested-With", "XMLHttpRequest")
    .build();
```

### 6. 박스스코어 교차 검증으로 KBO 사이트 내부 비일관성 발견

박스스코어 데이터가 정확한지 검증하기 위해, 박스스코어 누적값으로 직접 계산한 타율/ERA/WHIP를 KBO 시즌 통계와 비교하는 서비스를 만들었습니다. 결과를 `stat_validation_log` 테이블에 적재하는 방식.

처음 일치율은 타율 33%, ERA 80%, WHIP 51%였습니다 (허용 오차 0.001/0.05). 처음엔 박스스코어 수집과 KBO 시즌 통계 갱신의 시점 차이라고 가정했는데, 5/19 데이터를 동기화한 후에도 일치율이 거의 그대로였습니다.

박재현(KIA) 사례를 깊게 추적했습니다. 박스스코어 합산은 40경기 42안타, KBO 시즌 통계는 40경기 47안타. **경기 수가 같은데 안타 수가 5개 차이**가 났습니다. 시점 차이가 원인이었으면 경기 수도 달라야 했습니다.

KBO 공식 박스스코어 페이지에서 박재현이 출전한 5경기 (4/30, 4/26, 4/16, 5/05, 5/02)의 안타 수를 직접 1:1 대조한 결과, 우리 DB 값과 KBO 박스스코어 페이지 값이 100% 일치했습니다. 즉 차이는 우리 시스템이 아니라 **KBO 사이트 내부에 있었습니다**. KBO의 박스스코어 페이지와 시즌 통계 페이지가 서로 다른 집계 방식이나 시점을 사용하고 있던 거였습니다.

검증 도구가 없었다면 알아차리지 못했을 외부 데이터 소스의 한계였고, 우리 박스스코어 시스템의 정확성을 외부 검증으로 입증한 결과이기도 했습니다.

## 🤖 AI 협업 개발 방식

이 프로젝트는 Claude Code(Anthropic의 AI 코딩 어시스턴트)와 함께 개발했습니다. 코드를 받아 그대로 붙여넣는 게 아니라 설계, 구현, 디버깅 사이클을 같이 돌리는 페어 프로그래밍 방식으로 진행했습니다.

### 활용한 방식

요구사항을 정리해서 Claude Code에 전달하고 초기 구현을 받은 다음, 프로젝트 컨벤션과 기존 코드 스타일에 맞춰 검토·수정했습니다. 디버깅할 때는 콘솔 로그와 에러 메시지를 직접 분석해 가설을 세운 뒤 AI와 함께 검증하는 식으로 풀었습니다. 의사결정 단계에서는 AI가 제시한 여러 옵션 중 trade-off를 비교해 직접 선택했습니다.

### 직접 한 부분

외부 시스템의 구조 분석은 결국 사람이 브라우저 개발자 도구를 직접 열어야 했습니다. 네이버 스포츠의 React 빌드 해시 클래스, KBO 사이트의 ASP.NET 드롭다운 동작, KBO `.asmx` 엔드포인트의 봇 차단 헤더 같은 것들이 그렇습니다. 데이터 검증도 SQL 쿼리로 직접 이상 징후를 추적했습니다. 어떤 기능을 어디까지 만들지, 어떤 trade-off를 받아들일지 결정한 것도 제 몫이었습니다.

박스스코어 교차 검증 사례가 대표적입니다. 일치율이 33%로 나왔을 때 AI가 제시한 첫 가설(시점 차이)을 검증한 다음 그게 틀렸음을 데이터로 확인하고, KBO 공식 사이트에서 5경기 직접 대조까지 진행하면서 진짜 원인(외부 데이터 소스 비일관성)을 추적한 건 사람이 해야 하는 영역이었습니다.

### 느낀 점

AI가 만드는 코드의 품질은 결국 입력 컨텍스트의 품질에 비례한다는 걸 여러 번 체감했습니다. 디버깅의 핵심인 가설 수립과 원인 추적은 결국 사람이 해야 했고, AI를 코드 생성기보다는 페어 프로그래밍 파트너로 활용할 때 효과가 가장 컸습니다.

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

| Method | URL                              | 설명                             | 권한  |
| ------ | -------------------------------- | -------------------------------- | ----- |
| GET    | /api/v1/players                  | 선수 목록 조회                   | 모두  |
| GET    | /api/v1/players/{id}             | 선수 상세 조회                   | 모두  |
| POST   | /api/v1/players                  | 선수 등록                        | ADMIN |
| PUT    | /api/v1/players/{id}             | 선수 수정                        | ADMIN |
| DELETE | /api/v1/players/{id}             | 선수 삭제                        | ADMIN |
| GET    | /api/v1/games?from=&to=          | 경기 일정 조회                   | 모두  |
| GET    | /api/v1/games/{id}               | 경기 상세 조회                   | 모두  |
| POST   | /games/crawl                     | 경기 데이터 갱신                 | ADMIN |
| POST   | /players/crawl                   | 선수 데이터 갱신                 | ADMIN |
| POST   | /api/v1/chatbot                  | 챗봇 질의                        | 모두  |
| POST   | /signup                          | 회원가입                         | 익명  |
| GET    | /my/profile                      | 마이페이지                       | USER  |
| POST   | /my/team                         | 응원팀 변경                      | USER  |
| GET    | /api/v1/games/{id}/detail        | 경기 상세 + 박스스코어 조회      | 모두  |
| POST   | /crawling/boxscore?gameId={id}   | 단일 경기 박스스코어 수집        | ADMIN |
| POST   | /crawling/boxscore?date=YYYYMMDD | 날짜별 박스스코어 일괄 수집      | ADMIN |
| POST   | /crawling/cross-validation       | 박스스코어 ↔ 시즌 통계 교차 검증 | ADMIN |
