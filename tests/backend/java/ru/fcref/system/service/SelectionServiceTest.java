package ru.fcref.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.fcref.system.config.AppProperties;
import ru.fcref.system.domain.ActivationResult;
import ru.fcref.system.domain.Candidate;
import ru.fcref.system.domain.CandidateStatus;
import ru.fcref.system.domain.Invitation;
import ru.fcref.system.domain.Role;
import ru.fcref.system.domain.StageProgress;
import ru.fcref.system.domain.StageState;
import ru.fcref.system.domain.UserAccount;
import ru.fcref.system.domain.Verdict;
import ru.fcref.system.domain.VoteChoice;
import ru.fcref.system.domain.VoteStatus;

class SelectionServiceTest {

    private SelectionService service;
    private FakeUserDirectory userDirectory;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setMemberInvitationQuota(2);
        properties.setInvitationTtlDays(30);
        userDirectory = new FakeUserDirectory();
        service = new SelectionService(
                properties,
                userDirectory,
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
                .extracting("code")
                .isEqualTo("INVITATION_QUOTA_EXHAUSTED");
    }

    @Test
    void activatingInvitationRegistersCandidateAccountAndAssignsFirstStage() {
        Invitation invitation = service.createInvitation("member-1", "new candidate", "activation-request");

        ActivationResult result = service.activateInvitation(invitation.getToken(), "Candidate From Test");
        Candidate candidate = result.candidate();
        String interviewerUserId = candidate.currentStage().orElseThrow().getAssignedInterviewerUserId();

        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.IN_PROGRESS);
        assertThat(candidate.getInvitationId()).isEqualTo(invitation.getId());
        assertThat(candidate.getCandidateUserId()).isNotBlank();
        assertThat(interviewerUserId).isNotBlank();
        assertThat(userDirectory.findById(interviewerUserId).orElseThrow().getRoles()).contains(Role.MEMBER);
        assertThat(result.username()).startsWith("candidate");
        assertThat(result.password()).isNotBlank();
        assertThat(userDirectory.findByUsername(result.username()))
                .get()
                .extracting(UserAccount::getId)
                .isEqualTo(candidate.getCandidateUserId());
        assertThat(candidate.currentStage()).isPresent();
        assertThat(candidate.currentStage().orElseThrow().getState()).isEqualTo(StageState.AVAILABLE);
    }

    @Test
    void candidateCannotCreateInvitation() {
        assertThatThrownBy(() -> service.createInvitation("candidate-user-1", "candidate invite", "candidate-invite"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void adminCanCreateInvitationAsCommunityMember() {
        Invitation invitation = service.createInvitation("admin-1", "admin invite", "admin-invite");

        assertThat(invitation.getAuthorUserId()).isEqualTo("admin-1");
    }

    @Test
    void privilegedMemberCanCreateInvitationAsClubMember() {
        Invitation invitation = service.createInvitation("privileged-1", "privileged invite", "privileged-invite");

        assertThat(invitation.getAuthorUserId()).isEqualTo("privileged-1");
        assertThat(invitation.getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void candidateSnapshotContainsOnlyOwnedCandidate() {
        UserAccount candidate = userDirectory.findById("candidate-user-1").orElseThrow();

        SelectionSnapshot snapshot = service.snapshotFor(candidate);

        assertThat(snapshot.invitations()).isEmpty();
        assertThat(snapshot.candidates()).extracting(Candidate::getId).containsExactly("candidate-stage");
    }

    @Test
    void privilegedMemberCanVoteOnlyOncePerOpenVoting() {
        service.castVote("privileged-1", "candidate-vote", VoteChoice.SUPPORT, "Support");

        assertThatThrownBy(() -> service.castVote("privileged-1", "candidate-vote", VoteChoice.REJECT, "Changed my mind"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("VOTE_ALREADY_CAST");
    }

    @Test
    void blockedCandidateCannotSubmitStageResult() {
        service.blockCandidate(assignedInterviewer("candidate-stage"), "candidate-stage", "rules", "reason");

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
        assertThat(candidate("candidate-stage").getStatus()).isEqualTo(CandidateStatus.IN_REVIEW);
    }

    @Test
    void adminCannotSubmitStageResultForCandidate() {
        assertThatThrownBy(() -> service.submitStageResult("admin-1", "candidate-stage", "result", "admin-stage-result"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void passedVerdictOpensVotingAndPreventsDuplicateVerdict() {
        service.submitStageResult("candidate-user-1", "candidate-stage", "result", "review-stage-result");

        String interviewerUserId = assignedInterviewer("candidate-stage");
        StageProgress verdict = service.recordVerdict(interviewerUserId, "candidate-stage", Verdict.PASSED, "accepted");
        Candidate candidate = candidate("candidate-stage");

        assertThat(verdict.getState()).isEqualTo(StageState.PASSED);
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.VOTING);
        assertThat(candidate.openVotingSession()).isPresent();
        assertThat(candidate.openVotingSession().orElseThrow().getStatus()).isEqualTo(VoteStatus.OPEN);
        assertThatThrownBy(() -> service.recordVerdict(interviewerUserId, "candidate-stage", Verdict.PASSED, "again"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("STAGE_NOT_READY_FOR_VERDICT");
    }

    @Test
    void unassignedMemberCannotRecordVerdict() {
        service.submitStageResult("candidate-user-1", "candidate-stage", "result", "unassigned-review");

        assertThatThrownBy(() -> service.recordVerdict("member-1", "candidate-stage", Verdict.PASSED, "accepted"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void acceptedVoteAdvancesCandidateToNextStage() {
        service.submitStageResult("candidate-user-1", "candidate-stage", "result", "advance-stage-result");
        service.recordVerdict(assignedInterviewer("candidate-stage"), "candidate-stage", Verdict.PASSED, "accepted");
        service.castVote("privileged-1", "candidate-stage", VoteChoice.SUPPORT, "support");

        service.closeVote("admin-1", "candidate-stage");
        Candidate candidate = candidate("candidate-stage");

        assertThat(candidate.getCurrentStageId()).isEqualTo("interview");
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.IN_PROGRESS);
        assertThat(candidate.currentStage()).isPresent();
        assertThat(candidate.currentStage().orElseThrow().getState()).isEqualTo(StageState.AVAILABLE);
        assertThat(candidate.currentStage().orElseThrow().getAssignedInterviewerUserId()).isNotBlank();
    }

    @Test
    void finalAcceptedVotePromotesCandidateToMember() {
        service.submitStageResult("candidate-user-1", "candidate-stage", "result", "final-stage-result");
        service.recordVerdict(assignedInterviewer("candidate-stage"), "candidate-stage", Verdict.PASSED, "accepted");
        service.castVote("privileged-1", "candidate-stage", VoteChoice.SUPPORT, "support task");
        service.closeVote("admin-1", "candidate-stage");

        service.recordVerdict(assignedInterviewer("candidate-stage"), "candidate-stage", Verdict.PASSED, "interview accepted");
        service.castVote("privileged-1", "candidate-stage", VoteChoice.SUPPORT, "support interview");
        service.closeVote("admin-1", "candidate-stage");

        Candidate candidate = candidate("candidate-stage");
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.PASSED);
        assertThat(userDirectory.findById("candidate-user-1").orElseThrow().getRoles()).contains(Role.MEMBER);
        assertThat(userDirectory.findById("candidate-user-1").orElseThrow().getRoles()).doesNotContain(Role.CANDIDATE);
        assertThat(userDirectory.findById("member-1").orElseThrow().getRoles()).contains(Role.PRIVILEGED_MEMBER);
    }

    private Candidate candidate(String candidateId) {
        return service.snapshot().candidates().stream()
                .filter(candidate -> candidate.getId().equals(candidateId))
                .findFirst()
                .orElseThrow();
    }

    private String assignedInterviewer(String candidateId) {
        return candidate(candidateId).currentStage()
                .map(StageProgress::getAssignedInterviewerUserId)
                .orElseThrow();
    }

    private static final class FakeUserDirectory implements UserDirectory {

        private final Map<String, UserAccount> users = new LinkedHashMap<>();

        private FakeUserDirectory() {
            users.put("admin-1", new UserAccount("admin-1", "admin", "Administrator", EnumSet.of(Role.ADMIN, Role.MEMBER)));
            users.put("member-1", new UserAccount("member-1", "member", "Active club member", EnumSet.of(Role.MEMBER)));
            users.put(
                    "privileged-1",
                    new UserAccount(
                            "privileged-1",
                            "privileged",
                            "Privileged club member",
                            EnumSet.of(Role.MEMBER, Role.PRIVILEGED_MEMBER)
                    )
            );
            users.put(
                    "interviewer-1",
                    new UserAccount(
                            "interviewer-1",
                            "interviewer",
                            "Interviewer",
                            EnumSet.of(Role.INTERVIEWER, Role.MEMBER)
                    )
            );
            users.put("candidate-user-1", new UserAccount("candidate-user-1", "candidate", "Candidate", EnumSet.of(Role.CANDIDATE)));
        }

        @Override
        public List<UserAccount> listUsers() {
            return List.copyOf(users.values());
        }

        @Override
        public Optional<UserAccount> findById(String userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            return users.values().stream()
                    .filter(user -> user.getUsername().equals(username))
                    .findFirst();
        }

        @Override
        public UserAccount createUser(
                String userId,
                String username,
                String rawPassword,
                String displayName,
                Set<Role> roles
        ) {
            UserAccount user = new UserAccount(userId, username, displayName, roles);
            users.put(userId, user);
            return user;
        }

        @Override
        public UserAccount assignRole(String userId, Role role) {
            UserAccount user = users.get(userId);
            user.assignRole(role);
            return user;
        }

        @Override
        public UserAccount revokeRole(String userId, Role role) {
            UserAccount user = users.get(userId);
            user.revokeRole(role);
            return user;
        }
    }
}
