package com.example.monitor.api;

/**
 * Body of the "mark as OK" calls.
 *
 * @param by   who marks it (free text for now, required, at most 100 characters)
 * @param note optional, at most 500 characters (for example "scanner cleaned")
 */
public record AckRequest(String by, String note) {
}
