package ru.fcref.system.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Candidate {

    private final String id;
    private final String fullName;
    private final String invitationId;
    private final String invitedByUserId;
    private final Instant registeredAt;
    private CandidateStatus status;
    private String currentStageId;
    private final List<StageProgress> stages = new ArrayList<>();
    private final List<VotingSession> votingSessions = new ArrayList<>();
    private final List<Complaint> complaints = new ArrayList<>();
    private final List<BlockRecord> blocks = new ArrayList<>();

    public Candidate(
            String id,
            String fullName,
            String invitationId,
            String invitedByUserId,
            Instant registeredAt,
            CandidateStatus status,
            String currentStageId
    ) {
        this.id = id;
        this.fullName = fullName;
        this.invitationId = invitationId;
        this.invitedByUserId = invitedByUserId;
        this.registeredAt = registeredAt;
        this.status = status;
        this.currentStageId = currentStageId;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getInvitationId() {
        return invitationId;
    }

    public String getInvitedByUserId() {
        return invitedByUserId;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public CandidateStatus getStatus() {
        return status;
    }

    public void setStatus(CandidateStatus status) {
        this.status = status;
    }

    public String getCurrentStageId() {
        return currentStageId;
    }

    public void setCurrentStageId(String currentStageId) {
        this.currentStageId = currentStageId;
    }

    public List<StageProgress> getStages() {
        return stages;
    }

    public List<VotingSession> getVotingSessions() {
        return votingSessions;
    }

    public List<Complaint> getComplaints() {
        return complaints;
    }

    public List<BlockRecord> getBlocks() {
        return blocks;
    }

    public Optional<StageProgress> currentStage() {
        return stages.stream()
                .filter(stage -> stage.getStageId().equals(currentStageId))
                .findFirst();
    }

    public Optional<VotingSession> openVotingSession() {
        return votingSessions.stream()
                .filter(session -> session.getStatus() == VoteStatus.OPEN)
                .findFirst();
    }

    public Optional<BlockRecord> activeBlock() {
        return blocks.stream()
                .filter(BlockRecord::isActive)
                .findFirst();
    }
}
