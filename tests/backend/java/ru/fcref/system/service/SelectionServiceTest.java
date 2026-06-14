package ru.fcref.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.fcref.system.config.AppProperties;
import ru.fcref.system.domain.Candidate;
import ru.fcref.system.domain.CandidateStatus;
import ru.fcref.system.domain.Invitation;
import ru.fcref.system.domain.StageProgress;
import ru.fcref.system.domain.StageState;
import ru.fcref.system.domain.VoteChoice;

class SelectionServiceTest {

    private SelectionService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setMemberInvitationQuota(2);
        properties.setInvitationTtlDays(30);
        service = new SelectionService(
                properties,
                Clock.fixed(Instant.parse("2026-06-14T18:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createInvitationUsesQuotaAndIdempotencyKey() {
        Invitation first = service.createInvitation("member-1", "first", "same-request");
        Invitation duplicate = service.createInvitation("member-1", "first", "same-request");

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> service.createInvitation("member-1", "second", "another-request"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("квоты");
    }

    @Test
    void activatingInvitationRegistersCandidateAndAssignsFirstStage() {
        Invitation invitation = service.createInvitation("admin-1", "new candidate", "activation-request");

        Candidate candidate = service.activateInvitation(invitation.getToken(), "Сидоров Алексей");

        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.IN_PROGRESS);
        assertThat(candidate.getInvitationId()).isEqualTo(invitation.getId());
        assertThat(candidate.currentStage()).isPresent();
        assertThat(candidate.currentStage().orElseThrow().getState()).isEqualTo(StageState.AVAILABLE);
    }

    @Test
    void privilegedMemberCanVoteOnlyOncePerOpenVoting() {
        service.castVote("privileged-1", "candidate-vote", VoteChoice.SUPPORT, "Поддерживаю");

        assertThatThrownBy(() -> service.castVote("privileged-1", "candidate-vote", VoteChoice.REJECT, "Передумал"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("VOTE_ALREADY_CAST");
    }

    @Test
    void blockedCandidateCannotSubmitStageResult() {
        service.blockCandidate("interviewer-1", "candidate-stage", "Нарушение правил", "Основание");

        assertThatThrownBy(() -> service.submitStageResult("candidate-user-1", "candidate-stage", "result", "blocked-result"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_STATUS_LOCKED");
    }

    @Test
    void stageSubmissionIsIdempotentForSameRequestId() {
        StageProgress first = service.submitStageResult("candidate-user-1", "candidate-stage", "result", "same-stage-result");
        StageProgress duplicate = service.submitStageResult("candidate-user-1", "candidate-stage", "result", "same-stage-result");

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(duplicate.getState()).isEqualTo(StageState.SUBMITTED);
    }
}
