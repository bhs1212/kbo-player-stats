package com.kbo.stats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpDto {

    @NotBlank(message = "아이디를 입력해주세요")
    @Pattern(
        regexp = "^[a-zA-Z0-9_]{4,20}$",
        message = "영문/숫자/언더스코어 4~20자로 입력해주세요"
    )
    private String username;

    @NotBlank(message = "비밀번호를 입력해주세요")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력해주세요")
    private String passwordConfirm;

    @NotBlank(message = "응원 팀을 선택해주세요")
    @NotNull(message = "응원 팀을 선택해주세요")
    private String favoriteTeam;

    public boolean isPasswordMatching() {
        return password != null && password.equals(passwordConfirm);
    }
}
