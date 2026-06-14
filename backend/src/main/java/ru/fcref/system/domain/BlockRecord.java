package ru.fcref.system.domain;

import java.time.Instant;

public class BlockRecord {

    private final String id;
    private final String actorUserId;
    private final String category;
    private final String reason;
    private final Instant createdAt;
    private boolean active;
    private String resolvedByUserId;
    private String resolutionReason;
    private Instant resolvedAt;

    public BlockRecord(String id, String actorUserId, String category, String reason, Instant createdAt) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.category = category;
        this.reason = reason;
        this.createdAt = createdAt;
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public String getCategory() {
        return category;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public String getResolvedByUserId() {
        return resolvedByUserId;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void resolve(String resolvedByUserId, String resolutionReason, Instant resolvedAt) {
        this.active = false;
        this.resolvedByUserId = resolvedByUserId;
        this.resolutionReason = resolutionReason;
        this.resolvedAt = resolvedAt;
    }
}
