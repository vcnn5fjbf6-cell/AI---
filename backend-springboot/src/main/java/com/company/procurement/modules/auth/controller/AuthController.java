package com.company.procurement.modules.auth.controller;

import com.company.procurement.common.result.Result;
import com.company.procurement.modules.auth.dto.AuthLoginDTO;
import com.company.procurement.modules.auth.dto.AuthPasswordChangeDTO;
import com.company.procurement.modules.auth.dto.FeishuCallbackDTO;
import com.company.procurement.modules.auth.service.AuthService;
import com.company.procurement.modules.auth.vo.AuthLoginVO;
import com.company.procurement.modules.auth.vo.AuthUserVO;
import com.company.procurement.modules.auth.vo.FeishuLoginUrlVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<AuthLoginVO> login(@RequestBody @Valid AuthLoginDTO dto, HttpServletRequest request) {
        return Result.ok(authService.login(dto, resolveClientIp(request)));
    }

    @GetMapping("/feishu/login-url")
    public Result<FeishuLoginUrlVO> feishuLoginUrl(@RequestParam(required = false) String redirectUri) {
        return Result.ok(authService.feishuLoginUrl(redirectUri));
    }

    @PostMapping("/feishu/callback")
    public Result<AuthLoginVO> feishuCallback(@RequestBody @Valid FeishuCallbackDTO dto, HttpServletRequest request) {
        return Result.ok(authService.feishuCallback(dto, resolveClientIp(request)));
    }

    @GetMapping("/me")
    public Result<AuthUserVO> me() {
        return Result.ok(authService.currentUser());
    }

    @PostMapping("/password/change")
    public Result<Void> changePassword(@RequestBody @Valid AuthPasswordChangeDTO dto) {
        authService.changePassword(dto);
        return Result.ok();
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
