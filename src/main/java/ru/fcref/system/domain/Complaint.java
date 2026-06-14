package ru.fcref.system.domain;

import java.time.Instant;

public record Complaint(
        String id,
        String actorUserId,
        String reason,
        Instant createdAt
) {
}
