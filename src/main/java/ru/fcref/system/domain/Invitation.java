package ru.fcref.system.domain;

import java.time.Instant;

public class Invitation {

    private final String id;
    private final String token;
    private final String authorUserId;
    private final String comment;
    private final Instant createdAt;
    private final Instant expiresAt;
    private InvitationStatus status;
    private String activatedByCandidateId;
    private Instant activatedAt;

    public Invitation(
            String id,
            String token,
            String authorUserId,
            String comment,
            Instant createdAt,
            Instant expiresAt,
            InvitationStatus status
    ) {
        this.id = id;
        this.token = token;
        this.authorUserId = authorUserId;
        this.comment = comment;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public String getAuthorUserId() {
        return authorUserId;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public String getActivatedByCandidateId() {
        return activatedByCandidateId;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void activate(String candidateId, Instant activatedAt) {
        this.status = InvitationStatus.ACTIVATED;
        this.activatedByCandidateId = candidateId;
        this.activatedAt = activatedAt;
    }
}
