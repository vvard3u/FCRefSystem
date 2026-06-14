package ru.fcref.system.domain;

import java.time.Instant;

public record Vote(
        String id,
        String voterUserId,
        VoteChoice choice,
        String reason,
        Instant createdAt
) {
}
