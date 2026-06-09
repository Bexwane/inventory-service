// ─────────────────────────────────────────────────────────────
// FILE: ErrorResponse.java
// FIX: replaces plain String error body — structured, parseable by scanner apps
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.web.dto;

public record ErrorResponse(
        String code,           // machine-readable: "INSUFFICIENT_STOCK", "CONCURRENT_UPDATE"
        String message,        // human-readable: shown to supervisor on dashboard
        String correlationId   // trace ID: ops staff use this to find the log entry
) {}


