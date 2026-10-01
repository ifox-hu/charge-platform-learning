package com.chargeplatform.auth.controller;

import com.chargeplatform.auth.dto.AuthDtos;
import com.chargeplatform.auth.service.AuthService;
import com.chargeplatform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.success(service.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserProfile> me(Authentication authentication) {
        return ApiResponse.success(service.profile(authentication.getName()));
    }
    @PostMapping("/register")
    public ApiResponse<AuthDtos.LoginResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.created(service.register(request));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<AuthDtos.UserPage> users(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String role) {
        return ApiResponse.success(service.pageUsers(page, size, keyword, role));
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateUserStatus(@PathVariable Long id, @RequestParam boolean enabled,
                                              Authentication authentication) {
        service.setEnabled(id, enabled, authentication.getName());
        return ApiResponse.message(enabled ? "账号已启用" : "账号已禁用");
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                            @Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        service.resetPassword(id, request);
        return ApiResponse.message("密码已重置");
    }
}
