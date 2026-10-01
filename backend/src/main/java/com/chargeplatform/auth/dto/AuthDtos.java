package com.chargeplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    public record RegisterRequest(
            @jakarta.validation.constraints.Pattern(regexp = "[a-zA-Z0-9_]{3,30}", message = "用户名需为3至30位字母、数字或下划线") @NotBlank String username,
            @jakarta.validation.constraints.Size(min = 6, max = 72, message = "密码需为6至72位") @NotBlank String password,
            @jakarta.validation.constraints.Size(max = 50) @NotBlank String displayName) { }
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

    public record UserAdminView(Long id, String username, String displayName, String role, boolean enabled) {
    }

    public record UserPage(java.util.List<UserAdminView> rows, long total, long page, long size, long totalPages) {
    }

    public record ResetPasswordRequest(
            @jakarta.validation.constraints.Size(min = 6, max = 72, message = "密码需为6至72位") @NotBlank String password) {
    }
}
