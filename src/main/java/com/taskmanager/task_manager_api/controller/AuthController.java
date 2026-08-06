package com.taskmanager.task_manager_api.controller;

import com.taskmanager.task_manager_api.service.TokenBlocklistService;
import com.taskmanager.task_manager_api.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication endpoints — logout/token revocation.
 *
 * POST /auth/logout:
 *   Extracts jti from the JWT, stores it in the blocklist.
 *   Lambda Authorizer checks the blocklist on every subsequent request.
 *   The token becomes invalid immediately even though it hasn't expired.
 *
 * This solves the fundamental JWT weakness: tokens can't normally be
 * invalidated before their expiry time (1 hour for Cognito IdTokens).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

    private final TokenBlocklistService tokenBlocklistService;

    @PostMapping("/logout")
    @Operation(summary = "Logout — revokes the current JWT token immediately")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String token) {

        String userId = JwtUtil.extractUserId(token);
        log.info("Logout requested for userId={}", userId);

        // Add token to blocklist — Lambda Authorizer will deny it on next request
        tokenBlocklistService.revokeToken(token, userId);

        log.info("Token revoked for userId={}", userId);
        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully",
                "userId", userId
        ));
    }
}