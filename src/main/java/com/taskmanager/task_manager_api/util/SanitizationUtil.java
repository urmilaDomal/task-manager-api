package com.taskmanager.task_manager_api.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Input sanitization utility — strips XSS attack vectors from user input.
 *
 * Why sanitize?
 *   Task titles and descriptions are user-controlled strings. If they're
 *   ever rendered in a browser (e.g. a frontend reads the API and displays
 *   task titles), a malicious user could inject:
 *     {"title": "<script>document.cookie='stolen='+document.cookie</script>"}
 *   This is a stored XSS attack — the script runs in every victim's browser.
 *
 * What we strip:
 *   - <script> tags and their content
 *   - HTML event handlers (onclick, onload, onerror etc.)
 *   - javascript: protocol in attributes
 *   - All HTML tags (belt and suspenders)
 *
 * What we keep:
 *   - Normal text content
 *   - Punctuation and special characters
 *   - Unicode characters (important for international users)
 *
 * Note: For production-grade sanitization, consider using OWASP Java HTML
 * Sanitizer library which is more comprehensive and battle-tested.
 * This implementation covers the most common attack vectors.
 */
@Slf4j
public class SanitizationUtil {

    private SanitizationUtil() {
        // Utility class — no instances
    }

    /**
     * Sanitizes a user-provided string by removing XSS attack vectors.
     *
     * @param input raw user input
     * @return sanitized string safe to store and display
     */
    public static String sanitize(String input) {
        if (input == null) return null;

        String sanitized = input
                // Remove <script> tags and everything between them
                .replaceAll("(?i)<script[^>]*>.*?</script>", "")
                // Remove javascript: protocol
                .replaceAll("(?i)javascript:", "")
                // Remove HTML event handlers (onclick, onload, onerror, etc.)
                .replaceAll("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']", "")
                .replaceAll("(?i)on\\w+\\s*=\\s*\\w+", "")
                // Remove all remaining HTML tags
                .replaceAll("<[^>]*>", "")
                // Trim whitespace
                .trim();

        if (!sanitized.equals(input)) {
            log.warn("Input sanitized — potential XSS removed. Original length={} Sanitized length={}",
                    input.length(), sanitized.length());
        }

        return sanitized;
    }

    /**
     * Sanitizes and validates length in one call.
     * Convenient for DTO setters.
     */
    public static String sanitizeAndTrim(String input, int maxLength) {
        String sanitized = sanitize(input);
        if (sanitized != null && sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }
        return sanitized;
    }
}