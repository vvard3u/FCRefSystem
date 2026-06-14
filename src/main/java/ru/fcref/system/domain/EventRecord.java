package ru.fcref.system.domain;

import java.time.Instant;
import java.util.Map;

public record EventRecord(
        String id,
        EventType type,
        String actorUserId,
        String candidateId,
        String aggregateId,
        Map<String, Object> details,
        Instant occurredAt
) {
}
