package com.kbo.stats.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** BCrypt 패스워드 인코더 (UserAccountService, CustomUserDetailsService 에서 주입) */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // UserDetailsService 빈은 CustomUserDetailsService (@Service) 가 자동 등록됨

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // /api/** 경로는 CSRF 비활성화 (REST/AJAX 클라이언트 편의)
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher("/api/**")
                )
            )
            .authorizeHttpRequests(auth -> auth
                // ── 정적 리소스 및 공개 고정 경로 ──
                .requestMatchers(
                    "/",
                    "/login",
                    "/signup",
                    "/error",
                    "/favicon.ico",
                    "/css/**", "/js/**", "/images/**", "/webjars/**",
                    "/swagger-ui/**", "/swagger-ui.html",
                    "/v3/api-docs/**", "/api-docs/**"
                ).permitAll()
                // 챗봇 POST - Rate Limit으로 보호 중이므로 공개 허용
                .requestMatchers(new AntPathRequestMatcher("/api/chat", "POST")).permitAll()
                // 대시보드 API - 전체 공개
                .requestMatchers("/api/v1/dashboard/**").permitAll()

                // ── 회원 전용 경로 ──
                .requestMatchers("/my/**").hasAnyRole("USER", "ADMIN")

                // ── 관리자 전용 경로 (GET 전체 허용보다 먼저 선언해야 함) ──
                .requestMatchers(
                    new AntPathRequestMatcher("/players/new", "GET"),
                    new AntPathRequestMatcher("/players/*/edit", "GET"),
                    new AntPathRequestMatcher("/players", "POST"),
                    new AntPathRequestMatcher("/players/*", "POST"),
                    new AntPathRequestMatcher("/players/*/delete", "POST"),
                    new AntPathRequestMatcher("/players/import", "POST")
                ).hasRole("ADMIN")
                // PlayerApiController REST 변경 작업
                .requestMatchers(
                    new AntPathRequestMatcher("/api/v1/players", "POST"),
                    new AntPathRequestMatcher("/api/v1/players/**", "PUT"),
                    new AntPathRequestMatcher("/api/v1/players/**", "DELETE")
                ).hasRole("ADMIN")
                // CrawlingController (선수 크롤링)
                .requestMatchers(
                    new AntPathRequestMatcher("/crawling/run", "POST")
                ).hasRole("ADMIN")
                // BoxScoreCrawlController (박스스코어 수집)
                .requestMatchers(
                    new AntPathRequestMatcher("/crawling/boxscore", "POST")
                ).hasRole("ADMIN")
                // 경기 일정 크롤링 수동 트리거 (ADMIN 전용)
                .requestMatchers(
                    new AntPathRequestMatcher("/games/crawl", "POST")
                ).hasRole("ADMIN")

                // ── 나머지 GET 요청 전체 공개 허용 (목록·상세·랭킹·통계 등) ──
                .requestMatchers(new AntPathRequestMatcher("/**", "GET")).permitAll()

                // 그 외 모든 요청은 인증 필요 (안전 기본값)
                .anyRequest().authenticated()
            )
            // 폼 로그인
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            // 로그아웃 (POST + CSRF 포함)
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            )
            // 권한 부족(403) 시 홈으로 리다이렉트 (비로그인 → /login 은 Spring Security 기본 처리)
            .exceptionHandling(exception -> exception
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendRedirect(request.getContextPath() + "/players?accessDenied=true")
                )
            )
            // HTTP Basic 활성화 (Swagger UI / REST 테스트 편의)
            .httpBasic(basic -> {});

        return http.build();
    }
}
