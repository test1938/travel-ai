package dev.travelpilot.recommendation;

/** Why a place ranks where it does; the UI shows it without asking the LLM. Components are in [0, 1]. */
public record ScoreBreakdown(
        double total,
        double preference,
        double budget,
        double distance,
        double schedule,
        double group,
        double popularity,
        double rainBonus) {}
