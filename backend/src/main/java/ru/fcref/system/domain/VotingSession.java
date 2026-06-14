package ru.fcref.system.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class VotingSession {

    private final String id;
    private final String openedByUserId;
    private final Instant openedAt;
    private final Instant closesAt;
    private final int thresholdPercent;
    private final List<Vote> votes = new ArrayList<>();
    private VoteStatus status;
    private Boolean accepted;
    private Instant closedAt;
    private String closedByUserId;

    public VotingSession(String id, String openedByUserId, Instant openedAt, Instant closesAt, int thresholdPercent) {
        this.id = id;
        this.openedByUserId = openedByUserId;
        this.openedAt = openedAt;
        this.closesAt = closesAt;
        this.thresholdPercent = thresholdPercent;
        this.status = VoteStatus.OPEN;
    }

    public String getId() {
        return id;
    }

    public String getOpenedByUserId() {
        return openedByUserId;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public int getThresholdPercent() {
        return thresholdPercent;
    }

    public List<Vote> getVotes() {
        return votes;
    }

    public VoteStatus getStatus() {
        return status;
    }

    public Boolean getAccepted() {
        return accepted;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getClosedByUserId() {
        return closedByUserId;
    }

    public boolean hasVoteFrom(String voterUserId) {
        return votes.stream().anyMatch(vote -> vote.voterUserId().equals(voterUserId));
    }

    public void addVote(Vote vote) {
        votes.add(vote);
    }

    public void close(boolean accepted, String closedByUserId, Instant closedAt) {
        this.status = VoteStatus.CLOSED;
        this.accepted = accepted;
        this.closedByUserId = closedByUserId;
        this.closedAt = closedAt;
    }
}
