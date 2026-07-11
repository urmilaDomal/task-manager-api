package com.taskmanager.task_manager_api.util;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
 
import java.util.Base64;
 
/**
 * Extracts claims from a Cognito IdToken (JWT).
 *
 * A JWT has 3 parts separated by dots:
 *   header.payload.signature
 *
 * We only need the PAYLOAD (middle part), which is Base64-encoded JSON
 * containing claims like "sub" (user ID), "email", "exp" (expiry), etc.
 *
 * We do NOT verify the signature here — API Gateway's Cognito Authorizer
 * already verified it before the request even reached Lambda. Doing it
 * again would be redundant and require the Cognito public keys.
 */

@Slf4j
public class JwtUtil {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
 
    /**
     * Extracts the 'sub' claim — Cognito's unique, immutable user identifier.
     *
     * 'sub' is preferred over 'email' for ownership checks because:
     *   - It never changes (even if the user updates their email)
     *   - It's always present regardless of login method
     *
     * @param token The raw Authorization header value (the IdToken itself)
     * @return The user's Cognito sub (UUID format)
     */
    public static String extractUserId(String token) {
        try {
            // JWT structure: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }
 
            // Decode the payload — Base64URL encoded (uses - and _ instead of + and /)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
 
            // Parse the JSON and extract 'sub'
            JsonNode claims = objectMapper.readTree(payload);
            String sub = claims.get("sub").asText();
 
            log.debug("Extracted userId (sub): {}", sub);
            return sub;
 
        } catch (Exception e) {
            log.error("Failed to extract userId from token: {}", e.getMessage());
            throw new RuntimeException("Invalid token — could not extract user ID", e);
        }
    }
 
    /**
     * Extracts the user's email from the token.
     * Useful for logging/audit trails, but NOT for ownership checks
     * (emails can change — use sub instead).
     */
    public static String extractEmail(String token) {
        try {
            String[] parts = token.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = objectMapper.readTree(payload);
            return claims.get("email").asText();
        } catch (Exception e) {
            log.error("Failed to extract email from token: {}", e.getMessage());
            return "unknown";
        }
    }
 
    private JwtUtil() {
        // Utility class — no instances
    }
}
