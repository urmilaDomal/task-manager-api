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
     * Extracts the 'jti' claim — unique ID for this specific token.
     * Used for token revocation — stored in blocklist on logout.
     *
     * Every Cognito token has a unique jti — two tokens for the same
     * user have different jtis. This lets us revoke one token without
     * affecting others (e.g. user logged in on multiple devices).
     */
     public static String extractJti(String token) {
        return extractClaim(token, "jti");
    }
 
    /**
     * Extracts the 'exp' claim — token expiry as Unix epoch seconds.
     * Used to set DynamoDB TTL on blocklist entries so they auto-delete
     * when the token would have expired anyway.
     */
    public static long extractExp(String token) {
        try {
            JsonNode claims = decodePayload(token);
            return claims.get("exp").asLong();
        } catch (Exception e) {
            log.error("Failed to extract exp from token: {}", e.getMessage());
            throw new RuntimeException("Invalid token — could not extract expiry", e);
        }
    }
 
    /**
     * Extracts the user's email from the token.
     * Useful for logging/audit trails, but NOT for ownership checks
     * (emails can change — use sub instead).
     */
    public static String extractEmail(String token) {
        return extractClaim(token, "email");
    }

    // ── Private helpers ───────────────────────────────────────
 
    private static String extractClaim(String token, String claimName) {
        try {
            JsonNode claims = decodePayload(token);
            JsonNode claim = claims.get(claimName);
            if (claim == null) {
                log.warn("Claim '{}' not found in token", claimName);
                return null;
            }
            return claim.asText();
        } catch (Exception e) {
            log.error("Failed to extract '{}' from token: {}", claimName, e.getMessage());
            throw new RuntimeException("Invalid token — could not extract " + claimName, e);
        }
    }
 
    private static JsonNode decodePayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format — expected 3 parts");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        return objectMapper.readTree(payload);
    }
 
    private JwtUtil() {
        // Utility class — no instances
    }
}
