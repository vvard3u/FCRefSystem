package ru.fcref.system.domain;

import java.time.Instant;

public class StageProgress {

    private final String id;
    private final String stageId;
    private final String stageName;
    private final StageType stageType;
    private final int attemptLimit;
    private StageState state;
    private int attemptNumber;
    private String submittedResult;
    private Instant submittedAt;
    private Verdict verdict;
    private String report;
    private String decidedByUserId;
    private Instant decidedAt;
    private String assignedInterviewerUserId;

    public StageProgress(
            String id,
            String stageId,
            String stageName,
            StageType stageType,
            int attemptLimit,
            StageState state,
            int attemptNumber
    ) {
        this.id = id;
        this.stageId = stageId;
        this.stageName = stageName;
        this.stageType = stageType;
        this.attemptLimit = attemptLimit;
        this.state = state;
        this.attemptNumber = attemptNumber;
    }

    public String getId() {
        return id;
    }

    public String getStageId() {
        return stageId;
    }

    public String getStageName() {
        return stageName;
    }

    public StageType getStageType() {
        return stageType;
    }

    public int getAttemptLimit() {
        return attemptLimit;
    }

    public StageState getState() {
        return state;
    }

    public void setState(StageState state) {
        this.state = state;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getSubmittedResult() {
        return submittedResult;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void submit(String submittedResult, Instant submittedAt) {
        this.submittedResult = submittedResult;
        this.submittedAt = submittedAt;
        this.state = StageState.SUBMITTED;
        this.verdict = null;
        this.report = null;
        this.decidedByUserId = null;
        this.decidedAt = null;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getReport() {
        return report;
    }

    public String getDecidedByUserId() {
        return decidedByUserId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getAssignedInterviewerUserId() {
        return assignedInterviewerUserId;
    }

    public void assignInterviewer(String assignedInterviewerUserId) {
        this.assignedInterviewerUserId = assignedInterviewerUserId;
    }

    public void decide(Verdict verdict, String report, String decidedByUserId, Instant decidedAt) {
        this.verdict = verdict;
        this.report = report;
        this.decidedByUserId = decidedByUserId;
        this.decidedAt = decidedAt;
    }
}
