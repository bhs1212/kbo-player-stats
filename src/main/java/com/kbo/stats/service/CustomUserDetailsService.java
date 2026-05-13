package com.kbo.stats.service;

import com.kbo.stats.domain.UserAccount;
import com.kbo.stats.mapper.UserAccountMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    // 앱 시작 시 한 번만 인코딩 (요청마다 인코딩하는 비용 절감)
    private String encodedAdminPassword;

    @PostConstruct
    private void init() {
        encodedAdminPassword = passwordEncoder.encode(adminPassword);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 관리자 계정 우선 처리
        if (adminUsername.equals(username)) {
            log.debug("관리자 계정 인증 시도: {}", username);
            return User.withUsername(adminUsername)
                    .password(encodedAdminPassword)
                    .roles("ADMIN")
                    .build();
        }

        // DB에서 일반 사용자 조회
        UserAccount user = userAccountMapper.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        log.debug("일반 사용자 인증 시도: {}", username);
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }
}
