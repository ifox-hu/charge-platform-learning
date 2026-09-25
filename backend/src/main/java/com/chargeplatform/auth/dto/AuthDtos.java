package com.chargeplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank(message = "用户名不能为空") String username,
                               @NotBlank(message = "密码不能为空") String password) {
    }

    public record LoginResponse(String token, String tokenType, long expiresIn, String username, String displayName,
                                String role) {
    }

    public record UserProfile(String username, String displayName, String role) {
    }
}
