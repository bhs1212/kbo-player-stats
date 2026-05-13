package com.kbo.stats.service;

import com.kbo.stats.domain.UserAccount;
import com.kbo.stats.dto.SignUpDto;
import com.kbo.stats.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(SignUpDto dto) {
        if (userAccountMapper.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 사용자명입니다");
        }
        if (!dto.isPasswordMatching()) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }

        UserAccount user = UserAccount.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .favoriteTeam(dto.getFavoriteTeam())
                .role("USER")
                .build();

        userAccountMapper.insert(user);
        log.info("회원 가입 완료: {}", user.getUsername());
    }

    public UserAccount findByUsername(String username) {
        return userAccountMapper.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + username));
    }

    @Transactional
    public void updateFavoriteTeam(String username, String favoriteTeam) {
        userAccountMapper.updateFavoriteTeam(username, favoriteTeam);
        log.info("응원팀 변경: {} → {}", username, favoriteTeam);
    }
}
