package ru.fcref.system.domain;

public record SelectionStage(
        String id,
        String name,
        StageType type,
        int attemptLimit,
        int dueDays,
        Integer thresholdPercent,
        String criteria,
        boolean requiresSubmission
) {
}
