package dev.travelpilot.security;

/**
 * The anonymous traveler behind a request, taken from a verified guest token. Declare it as a controller parameter
 * to require a token (401 UNAUTHORIZED without one).
 */
public record Guest(String id) {}
