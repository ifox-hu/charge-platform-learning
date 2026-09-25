package com.chargeplatform.auth.controller;

import com.chargeplatform.auth.dto.AuthDtos;
import com.chargeplatform.auth.service.AuthService;
import com.chargeplatform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
}
