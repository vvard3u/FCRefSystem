package ru.fcref.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.fcref.system.domain.BlockRecord;
import ru.fcref.system.domain.Candidate;
import ru.fcref.system.domain.Complaint;
import ru.fcref.system.domain.EventRecord;
import ru.fcref.system.domain.Invitation;
import ru.fcref.system.domain.SelectionRegulation;
import ru.fcref.system.domain.SelectionStage;
import ru.fcref.system.domain.StageProgress;
import ru.fcref.system.domain.Vote;
import ru.fcref.system.domain.VotingSession;

@Repository
public class JdbcSelectionStateRepository implements SelectionStateRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSelectionStateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void replaceSnapshot(SelectionSnapshot snapshot) {
        clearStateTables();
        snapshot.regulations().forEach(this::insertRegulation);
        snapshot.invitations().forEach(this::insertInvitation);
        snapshot.candidates().forEach(this::insertCandidate);
    }

    @Override
    public void recordEvent(EventRecord event) {
        jdbcTemplate.update(
                """
                insert into selection_events (
                    id, event_type, actor_user_id, candidate_id, aggregate_id, details, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                event.id(),
                event.type().name(),
                event.actorUserId(),
                event.candidateId(),
                event.aggregateId(),
                json(event.details()),
                timestamp(event.occurredAt())
        );
    }

    private void clearStateTables() {
        List.of(
                "votes",
                "voting_sessions",
                "complaints",
                "block_records",
                "candidate_stage_progress",
                "candidates",
                "invitations",
                "selection_stage_rules",
                "selection_regulations"
        ).forEach(table -> jdbcTemplate.update("delete from " + table));
    }

    private void insertRegulation(SelectionRegulation regulation) {
        jdbcTemplate.update(
                """
                insert into selection_regulations (
                    id, name, description, created_at, created_by_user_id, active
                ) values (?, ?, ?, ?, ?, ?)
                """,
                regulation.getId(),
                regulation.getName(),
                regulation.getDescription(),
                timestamp(regulation.getCreatedAt()),
                regulation.getCreatedByUserId(),
                regulation.isActive()
        );
        List<SelectionStage> stages = regulation.getStages();
        for (int index = 0; index < stages.size(); index++) {
            insertStageRule(regulation.getId(), index, stages.get(index));
        }
    }

    private void insertStageRule(String regulationId, int index, SelectionStage stage) {
        jdbcTemplate.update(
                """
                insert into selection_stage_rules (
                    regulation_id, stage_id, stage_order, name, stage_type, attempt_limit, due_days,
                    threshold_percent, criteria, requires_submission
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                regulationId,
                stage.id(),
                index + 1,
                stage.name(),
                stage.type().name(),
                stage.attemptLimit(),
                stage.dueDays(),
                stage.thresholdPercent(),
                stage.criteria(),
                stage.requiresSubmission()
        );
    }

    private void insertInvitation(Invitation invitation) {
        jdbcTemplate.update(
                """
                insert into invitations (
                    id, token, author_user_id, comment, created_at, expires_at,
                    status, activated_by_candidate_id, activated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                invitation.getId(),
                invitation.getToken(),
                invitation.getAuthorUserId(),
                invitation.getComment(),
                timestamp(invitation.getCreatedAt()),
                timestamp(invitation.getExpiresAt()),
                invitation.getStatus().name(),
                invitation.getActivatedByCandidateId(),
                timestamp(invitation.getActivatedAt())
        );
    }

    private void insertCandidate(Candidate candidate) {
        jdbcTemplate.update(
                """
                insert into candidates (
                    id, full_name, candidate_user_id, invitation_id, invited_by_user_id,
                    registered_at, status, current_stage_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                candidate.getId(),
                candidate.getFullName(),
                candidate.getCandidateUserId(),
                candidate.getInvitationId(),
                candidate.getInvitedByUserId(),
                timestamp(candidate.getRegisteredAt()),
                candidate.getStatus().name(),
                candidate.getCurrentStageId()
        );
        candidate.getStages().forEach(stage -> insertStageProgress(candidate.getId(), stage));
        candidate.getVotingSessions().forEach(session -> insertVotingSession(candidate.getId(), session));
        candidate.getComplaints().forEach(complaint -> insertComplaint(candidate.getId(), complaint));
        candidate.getBlocks().forEach(block -> insertBlock(candidate.getId(), block));
    }

    private void insertStageProgress(String candidateId, StageProgress stage) {
        jdbcTemplate.update(
                """
                insert into candidate_stage_progress (
                    id, candidate_id, stage_id, stage_name, stage_type, attempt_limit, state,
                    attempt_number, submitted_result, submitted_at, verdict, report,
                    decided_by_user_id, decided_at, assigned_interviewer_user_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                stage.getId(),
                candidateId,
                stage.getStageId(),
                stage.getStageName(),
                stage.getStageType().name(),
                stage.getAttemptLimit(),
                stage.getState().name(),
                stage.getAttemptNumber(),
                stage.getSubmittedResult(),
                timestamp(stage.getSubmittedAt()),
                stage.getVerdict() != null ? stage.getVerdict().name() : null,
                stage.getReport(),
                stage.getDecidedByUserId(),
                timestamp(stage.getDecidedAt()),
                stage.getAssignedInterviewerUserId()
        );
    }

    private void insertVotingSession(String candidateId, VotingSession session) {
        jdbcTemplate.update(
                """
                insert into voting_sessions (
                    id, candidate_id, stage_id, opened_by_user_id, opened_at, closes_at,
                    threshold_percent, status, accepted, closed_at, closed_by_user_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                session.getId(),
                candidateId,
                session.getStageId(),
                session.getOpenedByUserId(),
                timestamp(session.getOpenedAt()),
                timestamp(session.getClosesAt()),
                session.getThresholdPercent(),
                session.getStatus().name(),
                session.getAccepted(),
                timestamp(session.getClosedAt()),
                session.getClosedByUserId()
        );
        session.getVotes().forEach(vote -> insertVote(session.getId(), vote));
    }

    private void insertVote(String votingSessionId, Vote vote) {
        jdbcTemplate.update(
                """
                insert into votes (
                    id, voting_session_id, voter_user_id, choice, reason, created_at
                ) values (?, ?, ?, ?, ?, ?)
                """,
                vote.id(),
                votingSessionId,
                vote.voterUserId(),
                vote.choice().name(),
                vote.reason(),
                timestamp(vote.createdAt())
        );
    }

    private void insertComplaint(String candidateId, Complaint complaint) {
        jdbcTemplate.update(
                """
                insert into complaints (
                    id, candidate_id, actor_user_id, reason, created_at
                ) values (?, ?, ?, ?, ?)
                """,
                complaint.id(),
                candidateId,
                complaint.actorUserId(),
                complaint.reason(),
                timestamp(complaint.createdAt())
        );
    }

    private void insertBlock(String candidateId, BlockRecord block) {
        jdbcTemplate.update(
                """
                insert into block_records (
                    id, candidate_id, actor_user_id, category, reason, created_at, active,
                    resolved_by_user_id, resolution_reason, resolved_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                block.getId(),
                candidateId,
                block.getActorUserId(),
                block.getCategory(),
                block.getReason(),
                timestamp(block.getCreatedAt()),
                block.isActive(),
                block.getResolvedByUserId(),
                block.getResolutionReason(),
                timestamp(block.getResolvedAt())
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize audit details", exception);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
