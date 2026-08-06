package com.taskmanager.task_manager_api.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for XSS sanitization — verifies malicious input is stripped
 * and normal input passes through unchanged.
 */
class SanitizationUtilTest {

    // ── Script tag removal ────────────────────────────────────

    @Test
    void sanitize_shouldRemoveScriptTags() {
        String input = "<script>alert('xss')</script>Hello";
        assertThat(SanitizationUtil.sanitize(input)).isEqualTo("Hello");
    }

    @Test
    void sanitize_shouldRemoveScriptTagsCaseInsensitive() {
        String input = "<SCRIPT>alert('xss')</SCRIPT>Hello";
        assertThat(SanitizationUtil.sanitize(input)).isEqualTo("Hello");
    }

    @Test
    void sanitize_shouldRemoveScriptTagsWithAttributes() {
        String input = "<script type='text/javascript'>alert('xss')</script>Safe";
        assertThat(SanitizationUtil.sanitize(input)).isEqualTo("Safe");
    }

    // ── Event handler removal ─────────────────────────────────

    @Test
    void sanitize_shouldRemoveOnClickHandler() {
        String input = "<img onclick=\"alert('xss')\">Hello";
        assertThat(SanitizationUtil.sanitize(input))
                .doesNotContain("onclick")
                .contains("Hello");
    }

    @Test
    void sanitize_shouldRemoveOnLoadHandler() {
        String input = "<body onload=\"alert('xss')\">content";
        assertThat(SanitizationUtil.sanitize(input))
                .doesNotContain("onload");
    }

    // ── Javascript protocol removal ───────────────────────────

    @Test
    void sanitize_shouldRemoveJavascriptProtocol() {
        String input = "<a href=\"javascript:alert('xss')\">click me</a>";
        assertThat(SanitizationUtil.sanitize(input))
                .doesNotContain("javascript:");
    }

    // ── HTML tag removal ──────────────────────────────────────

    @Test
    void sanitize_shouldRemoveHtmlTags() {
        String input = "<b>Bold</b> and <i>italic</i> text";
        assertThat(SanitizationUtil.sanitize(input))
                .isEqualTo("Bold and italic text");
    }

    // ── Normal input passes through ───────────────────────────

    @Test
    void sanitize_shouldPreserveNormalText() {
        String input = "Buy groceries and call mom";
        assertThat(SanitizationUtil.sanitize(input)).isEqualTo(input);
    }

    @Test
    void sanitize_shouldPreserveSpecialCharacters() {
        String input = "Price: $10.99 (50% off!)";
        assertThat(SanitizationUtil.sanitize(input)).isEqualTo(input);
    }

    @Test
    void sanitize_shouldPreserveUnicode() {
        String input = "Déjeuner avec François";
        assertThat(SanitizationUtil.sanitize(input)).isEqualTo(input);
    }

    @Test
    void sanitize_shouldHandleNull() {
        assertThat(SanitizationUtil.sanitize(null)).isNull();
    }

    @Test
    void sanitize_shouldHandleEmptyString() {
        assertThat(SanitizationUtil.sanitize("")).isEmpty();
    }

    // ── sanitizeAndTrim ───────────────────────────────────────

    @Test
    void sanitizeAndTrim_shouldTruncateToMaxLength() {
        String input = "A".repeat(150);
        String result = SanitizationUtil.sanitizeAndTrim(input, 100);
        assertThat(result).hasSize(100);
    }

    @Test
    void sanitizeAndTrim_shouldSanitizeBeforeTrimming() {
        // Script tag removed first, then length checked
        String input = "<script>alert('xss')</script>" + "A".repeat(50);
        String result = SanitizationUtil.sanitizeAndTrim(input, 100);
        assertThat(result).doesNotContain("script");
        assertThat(result.length()).isLessThanOrEqualTo(100);
    }
}