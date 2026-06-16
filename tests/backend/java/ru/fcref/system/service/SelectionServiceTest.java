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
        Invitation second = service.createInvitation("member-1", "second", "another-request");

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThatThrownBy(() -> service.createInvitation("member-1", "third", "third-request"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("INVITATION_QUOTA_EXHAUSTED");
    }

    @Test
    void initialSnapshotHasNoSeedCandidatesOrInvitations() {
        SelectionSnapshot snapshot = service.snapshot();

        assertThat(snapshot.invitations()).isEmpty();
        assertThat(snapshot.candidates()).isEmpty();
        assertThat(snapshot.regulations()).hasSize(1);
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
        assertThat(userDirectory.findById(interviewerUserId).orElseThrow().getRoles())
                .contains(Role.MEMBER, Role.PRIVILEGED_MEMBER)
                .doesNotContain(Role.ADMIN);
        assertThat(interviewerUserId).isNotEqualTo("member-1");
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
        Candidate candidate = activateCandidate();

        assertThatThrownBy(() -> service.createInvitation(candidate.getCandidateUserId(), "candidate invite", "candidate-invite"))
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
        Candidate activatedCandidate = activateCandidate();
        UserAccount candidate = userDirectory.findById(activatedCandidate.getCandidateUserId()).orElseThrow();

        SelectionSnapshot snapshot = service.snapshotFor(candidate);

        assertThat(snapshot.invitations()).isEmpty();
        assertThat(snapshot.candidates()).extracting(Candidate::getId).containsExactly(activatedCandidate.getId());
    }

    @Test
    void privilegedMemberCanVoteOnlyOncePerOpenVoting() {
        Candidate candidate = activateCandidate();
        moveCandidateToVoting(candidate);

        service.castVote("privileged-1", candidate.getId(), VoteChoice.SUPPORT, "Support");

        assertThatThrownBy(() -> service.castVote("privileged-1", candidate.getId(), VoteChoice.REJECT, "Changed my mind"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("VOTE_ALREADY_CAST");
    }

    @Test
    void blockedCandidateCannotSubmitStageResult() {
        Candidate candidate = activateCandidate();
        service.blockCandidate(assignedInterviewer(candidate.getId()), candidate.getId(), "rules", "reason");

        assertThatThrownBy(() -> service.submitStageResult(candidate.getCandidateUserId(), candidate.getId(), "result", "blocked-result"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CANDIDATE_STATUS_LOCKED");
    }

    @Test
    void stageSubmissionIsIdempotentForSameRequestId() {
        Candidate candidate = activateCandidate();
        StageProgress first = service.submitStageResult(
                candidate.getCandidateUserId(),
                candidate.getId(),
                "result",
                "same-stage-result"
        );
        StageProgress duplicate = service.submitStageResult(
                candidate.getCandidateUserId(),
                candidate.getId(),
                "result",
                "same-stage-result"
        );

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(duplicate.getState()).isEqualTo(StageState.SUBMITTED);
        assertThat(candidate(candidate.getId()).getStatus()).isEqualTo(CandidateStatus.IN_REVIEW);
    }

    @Test
    void adminCannotSubmitStageResultForCandidate() {
        Candidate candidate = activateCandidate();

        assertThatThrownBy(() -> service.submitStageResult("admin-1", candidate.getId(), "result", "admin-stage-result"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void passedVerdictOpensVotingAndPreventsDuplicateVerdict() {
        Candidate candidate = activateCandidate();
        service.submitStageResult(candidate.getCandidateUserId(), candidate.getId(), "result", "review-stage-result");

        String interviewerUserId = assignedInterviewer(candidate.getId());
        StageProgress verdict = service.recordVerdict(interviewerUserId, candidate.getId(), Verdict.PASSED, "accepted");
        Candidate updatedCandidate = candidate(candidate.getId());

        assertThat(verdict.getState()).isEqualTo(StageState.PASSED);
        assertThat(updatedCandidate.getStatus()).isEqualTo(CandidateStatus.VOTING);
        assertThat(updatedCandidate.openVotingSession()).isPresent();
        assertThat(updatedCandidate.openVotingSession().orElseThrow().getStatus()).isEqualTo(VoteStatus.OPEN);
        assertThatThrownBy(() -> service.recordVerdict(interviewerUserId, candidate.getId(), Verdict.PASSED, "again"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("STAGE_NOT_READY_FOR_VERDICT");
    }

    @Test
    void unassignedMemberCannotRecordVerdict() {
        Candidate candidate = activateCandidate();
        service.submitStageResult(candidate.getCandidateUserId(), candidate.getId(), "result", "unassigned-review");

        assertThatThrownBy(() -> service.recordVerdict("member-1", candidate.getId(), Verdict.PASSED, "accepted"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void acceptedVoteAdvancesCandidateToNextStage() {
        Candidate candidate = activateCandidate();
        service.submitStageResult(candidate.getCandidateUserId(), candidate.getId(), "result", "advance-stage-result");
        service.recordVerdict(assignedInterviewer(candidate.getId()), candidate.getId(), Verdict.PASSED, "accepted");
        service.castVote("privileged-1", candidate.getId(), VoteChoice.SUPPORT, "support");

        service.closeVote("admin-1", candidate.getId());
        Candidate updatedCandidate = candidate(candidate.getId());

        assertThat(updatedCandidate.getCurrentStageId()).isEqualTo("task");
        assertThat(updatedCandidate.getStatus()).isEqualTo(CandidateStatus.IN_PROGRESS);
        assertThat(updatedCandidate.currentStage()).isPresent();
        assertThat(updatedCandidate.currentStage().orElseThrow().getState()).isEqualTo(StageState.AVAILABLE);
        assertThat(updatedCandidate.currentStage().orElseThrow().getAssignedInterviewerUserId()).isNotBlank();
    }

    @Test
    void finalAcceptedVotePromotesCandidateToMember() {
        Candidate candidate = activateCandidate();
        service.submitStageResult(candidate.getCandidateUserId(), candidate.getId(), "result", "final-stage-result");
        service.recordVerdict(assignedInterviewer(candidate.getId()), candidate.getId(), Verdict.PASSED, "accepted");
        service.castVote("privileged-1", candidate.getId(), VoteChoice.SUPPORT, "support task");
        service.closeVote("admin-1", candidate.getId());

        service.submitStageResult(candidate.getCandidateUserId(), candidate.getId(), "task result", "final-task-result");
        service.recordVerdict(assignedInterviewer(candidate.getId()), candidate.getId(), Verdict.PASSED, "task accepted");
        service.castVote("privileged-1", candidate.getId(), VoteChoice.SUPPORT, "support task stage");
        service.closeVote("admin-1", candidate.getId());

        service.recordVerdict(assignedInterviewer(candidate.getId()), candidate.getId(), Verdict.PASSED, "interview accepted");
        service.castVote("privileged-1", candidate.getId(), VoteChoice.SUPPORT, "support interview");
        service.closeVote("admin-1", candidate.getId());

        Candidate updatedCandidate = candidate(candidate.getId());
        assertThat(updatedCandidate.getStatus()).isEqualTo(CandidateStatus.PASSED);
        assertThat(userDirectory.findById(candidate.getCandidateUserId()).orElseThrow().getRoles()).contains(Role.MEMBER);
        assertThat(userDirectory.findById(candidate.getCandidateUserId()).orElseThrow().getRoles()).doesNotContain(Role.CANDIDATE);
        assertThat(userDirectory.findById("member-1").orElseThrow().getRoles()).contains(Role.PRIVILEGED_MEMBER);
    }

    private Candidate activateCandidate() {
        return activateCandidateResult().candidate();
    }

    private ActivationResult activateCandidateResult() {
        Invitation invitation = service.createInvitation("member-1", "candidate", "activate-candidate");
        return service.activateInvitation(invitation.getToken(), "Candidate From Test");
    }

    private void moveCandidateToVoting(Candidate candidate) {
        service.submitStageResult(candidate.getCandidateUserId(), candidate.getId(), "result", "move-to-voting");
        service.recordVerdict(assignedInterviewer(candidate.getId()), candidate.getId(), Verdict.PASSED, "accepted");
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
                    "privileged-2",
                    new UserAccount(
                            "privileged-2",
                            "privileged2",
                            "Second privileged club member",
                            EnumSet.of(Role.MEMBER, Role.PRIVILEGED_MEMBER)
                    )
            );
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
