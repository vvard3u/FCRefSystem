package ru.fcref.system.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.fcref.system.config.AppProperties;
import ru.fcref.system.domain.ActivationResult;
import ru.fcref.system.domain.BlockRecord;
import ru.fcref.system.domain.Candidate;
import ru.fcref.system.domain.CandidateStatus;
import ru.fcref.system.domain.Complaint;
import ru.fcref.system.domain.EventRecord;
import ru.fcref.system.domain.EventType;
import ru.fcref.system.domain.Invitation;
import ru.fcref.system.domain.InvitationStatus;
import ru.fcref.system.domain.Role;
import ru.fcref.system.domain.SelectionRegulation;
import ru.fcref.system.domain.SelectionStage;
import ru.fcref.system.domain.StageProgress;
import ru.fcref.system.domain.StageState;
import ru.fcref.system.domain.StageType;
import ru.fcref.system.domain.UserAccount;
import ru.fcref.system.domain.Verdict;
import ru.fcref.system.domain.Vote;
import ru.fcref.system.domain.VoteChoice;
import ru.fcref.system.domain.VotingSession;

@Service
public class SelectionService {

    private final AppProperties properties;
    private final Clock clock;
    private final UserDirectory userDirectory;
    private final SelectionStateRepository stateRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Invitation> invitations = new LinkedHashMap<>();
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();
    private final Map<String, SelectionRegulation> regulations = new LinkedHashMap<>();
    private final List<EventRecord> events = new ArrayList<>();
    private final Map<String, String> idempotencyIndex = new LinkedHashMap<>();
    private int invitationSequence = 100;
    private int candidateSequence = 100;
    private int regulationSequence = 1;
    private int eventSequence = 1;
    private int stageProgressSequence = 100;
    private int voteSequence = 100;
    private int blockSequence = 100;
    private int complaintSequence = 100;
    private int votingSessionSequence = 100;

    @Autowired
    public SelectionService(
            AppProperties properties,
            UserDirectory userDirectory,
            SelectionStateRepository stateRepository
    ) {
        this(properties, userDirectory, Clock.systemUTC(), stateRepository);
    }

    public SelectionService(AppProperties properties, UserDirectory userDirectory, Clock clock) {
        this(properties, userDirectory, clock, SelectionStateRepository.noop());
    }

    public SelectionService(
            AppProperties properties,
            UserDirectory userDirectory,
            Clock clock,
            SelectionStateRepository stateRepository
    ) {
        this.properties = properties;
        this.userDirectory = userDirectory;
        this.clock = clock;
        this.stateRepository = stateRepository;
        seed();
        stateRepository.replaceSnapshot(snapshot());
    }

    public synchronized SelectionSnapshot snapshot() {
        return new SelectionSnapshot(
                userDirectory.listUsers(),
                List.copyOf(invitations.values()),
                List.copyOf(candidates.values()),
                List.copyOf(regulations.values()),
                events.stream()
                        .sorted(Comparator.comparing(EventRecord::occurredAt).reversed())
                        .toList()
        );
    }

    public synchronized SelectionSnapshot snapshotFor(UserAccount viewer) {
        if (viewer.hasRole(Role.ADMIN)) {
            return snapshot();
        }

        List<Candidate> visibleCandidates = visibleCandidates(viewer);
        List<Invitation> visibleInvitations = visibleInvitations(viewer);
        Set<String> visibleCandidateIds = visibleCandidates.stream()
                .map(Candidate::getId)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        Set<String> visibleInvitationIds = visibleInvitations.stream()
                .map(Invitation::getId)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        return new SelectionSnapshot(
                visibleUsers(viewer, visibleCandidates, visibleInvitations),
                visibleInvitations,
                visibleCandidates,
                List.of(activeRegulation()),
                visibleEvents(viewer, visibleCandidateIds, visibleInvitationIds)
        );
    }

    public synchronized Invitation createInvitation(String actorUserId, String comment, String requestId) {
        UserAccount actor = requireRole(actorUserId, Role.MEMBER);
        if (requestId != null && !requestId.isBlank()) {
            String key = "invitation:" + actor.getId() + ":" + requestId;
            String existingId = idempotencyIndex.get(key);
            if (existingId != null) {
                return requireInvitation(existingId);
            }
        }

        long usedQuota = invitations.values().stream()
                .filter(invitation -> invitation.getAuthorUserId().equals(actor.getId()))
                .filter(invitation -> invitation.getStatus() == InvitationStatus.ACTIVE)
                .count();
        if (usedQuota >= properties.getMemberInvitationQuota()) {
            throw new BusinessRuleException("INVITATION_QUOTA_EXHAUSTED", "У участника нет доступной квоты приглашений");
        }

        Instant now = now();
        invitationSequence++;
        Invitation invitation = new Invitation(
                "inv-" + invitationSequence,
                "bk-" + invitationSequence,
                actor.getId(),
                normalizeOptional(comment),
                now,
                now.plus(properties.getInvitationTtlDays(), ChronoUnit.DAYS),
                InvitationStatus.ACTIVE
        );
        invitations.put(invitation.getId(), invitation);
        if (requestId != null && !requestId.isBlank()) {
            idempotencyIndex.put("invitation:" + actor.getId() + ":" + requestId, invitation.getId());
        }
        event(EventType.INVITATION_CREATED, actor.getId(), null, invitation.getId(), Map.of("token", invitation.getToken()));
        return invitation;
    }

    public synchronized ActivationResult activateInvitation(String token, String fullName) {
        Invitation invitation = invitations.values().stream()
                .filter(value -> value.getToken().equals(token))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("INVITATION_NOT_FOUND", "Приглашение не найдено"));
        requireText(fullName, "fullName", "ФИО кандидата обязательно");
        Instant now = now();
        if (invitation.getStatus() != InvitationStatus.ACTIVE) {
            throw new BusinessRuleException("INVITATION_NOT_ACTIVE", "Приглашение уже использовано, отменено или истекло");
        }
        if (invitation.getExpiresAt().isBefore(now)) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            throw new BusinessRuleException("INVITATION_EXPIRED", "Срок действия приглашения истек");
        }

        SelectionRegulation regulation = activeRegulation();
        SelectionStage firstStage = regulation.getStages().get(0);
        candidateSequence++;
        String candidateId = "candidate-" + candidateSequence;
        String candidateUserId = "candidate-user-" + candidateSequence;
        String candidateUsername = "candidate" + candidateSequence;
        String candidatePassword = generatePassword();
        UserAccount account = userDirectory.createUser(
                candidateUserId,
                candidateUsername,
                candidatePassword,
                fullName.trim(),
                EnumSet.of(Role.CANDIDATE)
        );
        Candidate candidate = new Candidate(
                candidateId,
                fullName.trim(),
                account.getId(),
                invitation.getId(),
                invitation.getAuthorUserId(),
                now,
                CandidateStatus.IN_PROGRESS,
                firstStage.id()
        );
        StageProgress firstProgress = progressFrom(firstStage, StageState.AVAILABLE, 1);
        assignRandomInterviewer(candidate, firstProgress);
        candidate.getStages().add(firstProgress);
        candidates.put(candidate.getId(), candidate);
        invitation.activate(candidate.getId(), now);

        event(EventType.INVITATION_ACTIVATED, null, candidate.getId(), invitation.getId(), Map.of("token", token));
        event(EventType.CANDIDATE_REGISTERED, null, candidate.getId(), candidate.getId(), Map.of("fullName", candidate.getFullName()));
        event(EventType.STAGE_ASSIGNED, null, candidate.getId(), firstStage.id(), stageAssignedDetails(firstStage, firstProgress));
        return new ActivationResult(candidate, candidateUsername, candidatePassword);
    }

    public synchronized SelectionRegulation createRegulation(
            String actorUserId,
            String name,
            String description,
            List<SelectionStage> stages
    ) {
        UserAccount actor = requireRole(actorUserId, Role.ADMIN);
        requireText(name, "name", "Название регламента обязательно");
        validateRegulationStages(stages);

        regulations.values().forEach(regulation -> regulation.setActive(false));
        regulationSequence++;
        SelectionRegulation regulation = new SelectionRegulation(
                "reg-" + regulationSequence,
                name.trim(),
                normalizeOptional(description),
                now(),
                actor.getId(),
                stages,
                true
        );
        regulations.put(regulation.getId(), regulation);
        event(
                EventType.REGULATION_CHANGED,
                actor.getId(),
                null,
                regulation.getId(),
                Map.of("name", regulation.getName(), "stageCount", regulation.getStages().size())
        );
        return regulation;
    }

    public synchronized StageProgress submitStageResult(String actorUserId, String candidateId, String result, String requestId) {
        UserAccount actor = requireRole(actorUserId, Role.CANDIDATE);
        requireText(result, "result", "Результат этапа обязателен");
        Candidate candidate = requireCandidate(candidateId);
        ensureCandidateOwnsRecord(actor, candidate);
        ensureCandidateCanMove(candidate);
        StageProgress current = requireCurrentStage(candidate);
        SelectionStage stage = requireActiveStage(current.getStageId());
        if (requestId != null && !requestId.isBlank()) {
            String key = "stage-result:" + candidate.getId() + ":" + current.getStageId() + ":" + requestId;
            if (idempotencyIndex.containsKey(key)) {
                return current;
            }
        }
        if (!stage.requiresSubmission()) {
            throw new BusinessRuleException("STAGE_DOES_NOT_ACCEPT_RESULT", "Текущий этап не принимает результат через систему");
        }
        if (current.getState() != StageState.AVAILABLE && current.getState() != StageState.RETRY) {
            throw new BusinessRuleException("STAGE_NOT_AVAILABLE", "Текущий этап недоступен для отправки результата");
        }
        if (requestId != null && !requestId.isBlank()) {
            String key = "stage-result:" + candidate.getId() + ":" + current.getStageId() + ":" + requestId;
            idempotencyIndex.put(key, current.getId());
        }
        current.submit(result.trim(), now());
        candidate.setStatus(CandidateStatus.IN_REVIEW);
        event(
                EventType.STAGE_RESULT_SUBMITTED,
                actor.getId(),
                candidate.getId(),
                current.getId(),
                Map.of("stageName", current.getStageName(), "attempt", current.getAttemptNumber())
        );
        return current;
    }

    public synchronized StageProgress recordVerdict(String actorUserId, String candidateId, Verdict verdict, String report) {
        UserAccount actor = requireUser(actorUserId);
        Objects.requireNonNull(verdict, "verdict");
        requireText(report, "report", "Отчет по вердикту обязателен");
        Candidate candidate = requireCandidate(candidateId);
        ensureCandidateCanMove(candidate);
        StageProgress current = requireCurrentStage(candidate);
        requireAssignedInterviewerOrAdmin(actor, current);
        SelectionStage stage = requireActiveStage(current.getStageId());
        ensureStageReadyForVerdict(current, stage);
        current.decide(verdict, report.trim(), actorUserId, now());

        if (verdict == Verdict.PASSED) {
            current.setState(StageState.PASSED);
            openVotingForCurrentStage(candidate, actorUserId);
        } else if (verdict == Verdict.RETRY && current.getAttemptNumber() < current.getAttemptLimit()) {
            current.setAttemptNumber(current.getAttemptNumber() + 1);
            current.setState(StageState.RETRY);
            candidate.setStatus(CandidateStatus.IN_PROGRESS);
        } else {
            current.setState(StageState.FAILED);
            candidate.setStatus(CandidateStatus.FAILED);
        }

        event(
                EventType.VERDICT_RECORDED,
                actorUserId,
                candidate.getId(),
                current.getId(),
                Map.of("stageName", current.getStageName(), "verdict", verdict.name())
        );
        return current;
    }

    public synchronized VotingSession openVote(String actorUserId, String candidateId) {
        requireRole(actorUserId, Role.ADMIN);
        Candidate candidate = requireCandidate(candidateId);
        ensureCandidateCanMove(candidate);
        Optional<VotingSession> existing = candidate.openVotingSession();
        if (existing.isPresent()) {
            return existing.get();
        }

        return openVotingForCurrentStage(candidate, actorUserId);
    }

    public synchronized Vote castVote(String actorUserId, String candidateId, VoteChoice choice, String reason) {
        requireRole(actorUserId, Role.PRIVILEGED_MEMBER);
        Objects.requireNonNull(choice, "choice");
        requireText(reason, "reason", "Пояснение голоса обязательно");
        Candidate candidate = requireCandidate(candidateId);
        VotingSession session = candidate.openVotingSession()
                .orElseThrow(() -> new BusinessRuleException("VOTE_NOT_OPEN", "По кандидату нет открытого голосования"));
        if (session.hasVoteFrom(actorUserId)) {
            throw new BusinessRuleException("VOTE_ALREADY_CAST", "У пользователя уже есть актуальный голос");
        }

        voteSequence++;
        Vote vote = new Vote("vote-" + voteSequence, actorUserId, choice, reason.trim(), now());
        session.addVote(vote);
        event(
                EventType.VOTE_CAST,
                actorUserId,
                candidate.getId(),
                session.getId(),
                Map.of("choice", choice.name())
        );
        return vote;
    }

    public synchronized VotingSession closeVote(String actorUserId, String candidateId) {
        requireRole(actorUserId, Role.ADMIN);
        Candidate candidate = requireCandidate(candidateId);
        VotingSession session = candidate.openVotingSession()
                .orElseThrow(() -> new BusinessRuleException("VOTE_NOT_OPEN", "По кандидату нет открытого голосования"));
        long supportCount = session.getVotes().stream()
                .filter(vote -> vote.choice() == VoteChoice.SUPPORT)
                .count();
        int total = session.getVotes().size();
        boolean accepted = total > 0 && supportCount * 100 >= (long) session.getThresholdPercent() * total;
        session.close(accepted, actorUserId, now());

        StageProgress current = requireCurrentStage(candidate);
        if (accepted) {
            current.setState(StageState.PASSED);
            advanceCandidate(candidate, actorUserId);
        } else {
            current.setState(StageState.FAILED);
            candidate.setStatus(CandidateStatus.FAILED);
        }
        event(
                EventType.VOTE_CLOSED,
                actorUserId,
                candidate.getId(),
                session.getId(),
                Map.of("accepted", accepted, "supportVotes", supportCount, "totalVotes", total)
        );
        return session;
    }

    public synchronized Complaint createComplaint(String actorUserId, String candidateId, String reason) {
        requireRole(actorUserId, Role.PRIVILEGED_MEMBER);
        requireText(reason, "reason", "Причина жалобы обязательна");
        Candidate candidate = requireCandidate(candidateId);
        complaintSequence++;
        Complaint complaint = new Complaint("complaint-" + complaintSequence, actorUserId, reason.trim(), now());
        candidate.getComplaints().add(complaint);
        event(EventType.COMPLAINT_CREATED, actorUserId, candidate.getId(), complaint.id(), Map.of("reason", complaint.reason()));
        return complaint;
    }

    public synchronized BlockRecord blockCandidate(String actorUserId, String candidateId, String category, String reason) {
        UserAccount actor = requireUser(actorUserId);
        requireText(category, "category", "Категория блокировки обязательна");
        requireText(reason, "reason", "Причина блокировки обязательна");
        Candidate candidate = requireCandidate(candidateId);
        requireAssignedInterviewerOrAdmin(actor, requireCurrentStage(candidate));
        if (candidate.getStatus() == CandidateStatus.BLOCKED) {
            throw new BusinessRuleException("CANDIDATE_ALREADY_BLOCKED", "Кандидат уже заблокирован");
        }
        blockSequence++;
        BlockRecord block = new BlockRecord("block-" + blockSequence, actorUserId, category.trim(), reason.trim(), now());
        candidate.getBlocks().add(block);
        candidate.setStatus(CandidateStatus.BLOCKED);
        event(
                EventType.CANDIDATE_BLOCKED,
                actorUserId,
                candidate.getId(),
                block.getId(),
                Map.of("category", block.getCategory(), "reason", block.getReason())
        );
        return block;
    }

    public synchronized BlockRecord unblockCandidate(String actorUserId, String candidateId, String reason) {
        requireRole(actorUserId, Role.ADMIN);
        requireText(reason, "reason", "Причина снятия блокировки обязательна");
        Candidate candidate = requireCandidate(candidateId);
        BlockRecord block = candidate.activeBlock()
                .orElseThrow(() -> new BusinessRuleException("CANDIDATE_NOT_BLOCKED", "У кандидата нет активной блокировки"));
        block.resolve(actorUserId, reason.trim(), now());
        candidate.setStatus(CandidateStatus.IN_PROGRESS);
        event(
                EventType.CANDIDATE_UNBLOCKED,
                actorUserId,
                candidate.getId(),
                block.getId(),
                Map.of("reason", reason.trim())
        );
        return block;
    }

    public synchronized UserAccount assignRole(String actorUserId, String targetUserId, Role role) {
        requireRole(actorUserId, Role.ADMIN);
        Objects.requireNonNull(role, "role");
        UserAccount target = userDirectory.assignRole(targetUserId, role);
        event(EventType.ROLE_ASSIGNED, actorUserId, null, target.getId(), Map.of("role", role.name()));
        return target;
    }

    public synchronized UserAccount revokeRole(String actorUserId, String targetUserId, Role role) {
        requireRole(actorUserId, Role.ADMIN);
        Objects.requireNonNull(role, "role");
        UserAccount target = userDirectory.revokeRole(targetUserId, role);
        event(EventType.ROLE_REVOKED, actorUserId, null, target.getId(), Map.of("role", role.name()));
        return target;
    }

    private List<Candidate> visibleCandidates(UserAccount viewer) {
        if (viewer.hasRole(Role.PRIVILEGED_MEMBER)) {
            return List.copyOf(candidates.values());
        }
        if (viewer.hasRole(Role.MEMBER) || viewer.hasRole(Role.CANDIDATE)) {
            return candidates.values().stream()
                    .filter(candidate -> viewer.getId().equals(candidate.getInvitedByUserId())
                            || viewer.getId().equals(candidate.getCandidateUserId())
                            || isCurrentStageAssignedTo(candidate, viewer.getId()))
                    .toList();
        }
        return List.of();
    }

    private List<Invitation> visibleInvitations(UserAccount viewer) {
        if (viewer.hasRole(Role.MEMBER)) {
            return invitations.values().stream()
                    .filter(invitation -> viewer.getId().equals(invitation.getAuthorUserId()))
                    .toList();
        }
        return List.of();
    }

    private List<UserAccount> visibleUsers(
            UserAccount viewer,
            List<Candidate> visibleCandidates,
            List<Invitation> visibleInvitations
    ) {
        if (viewer.hasRole(Role.PRIVILEGED_MEMBER)) {
            return userDirectory.listUsers();
        }

        Set<String> userIds = new LinkedHashSet<>();
        userIds.add(viewer.getId());
        visibleCandidates.stream()
                .map(Candidate::getInvitedByUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        visibleCandidates.stream()
                .map(Candidate::getCandidateUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        visibleCandidates.stream()
                .flatMap(candidate -> candidate.getStages().stream())
                .map(StageProgress::getAssignedInterviewerUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        visibleInvitations.stream()
                .map(Invitation::getAuthorUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);

        return userDirectory.listUsers().stream()
                .filter(user -> userIds.contains(user.getId()))
                .toList();
    }

    private List<EventRecord> visibleEvents(
            UserAccount viewer,
            Set<String> visibleCandidateIds,
            Set<String> visibleInvitationIds
    ) {
        return events.stream()
                .filter(event -> canSeeEvent(viewer, event, visibleCandidateIds, visibleInvitationIds))
                .sorted(Comparator.comparing(EventRecord::occurredAt).reversed())
                .toList();
    }

    private boolean canSeeEvent(
            UserAccount viewer,
            EventRecord event,
            Set<String> visibleCandidateIds,
            Set<String> visibleInvitationIds
    ) {
        if (viewer.hasRole(Role.PRIVILEGED_MEMBER)) {
            return true;
        }
        if (viewer.hasRole(Role.CANDIDATE) && isInternalCandidateEvent(event.type())) {
            return false;
        }
        return viewer.getId().equals(event.actorUserId())
                || visibleCandidateIds.contains(event.candidateId())
                || visibleInvitationIds.contains(event.aggregateId());
    }

    private boolean isInternalCandidateEvent(EventType type) {
        return type == EventType.VOTE_OPENED
                || type == EventType.VOTE_CAST
                || type == EventType.VOTE_CLOSED
                || type == EventType.COMPLAINT_CREATED
                || type == EventType.ROLE_ASSIGNED
                || type == EventType.ROLE_REVOKED;
    }

    private void seed() {
        SelectionRegulation defaultRegulation = new SelectionRegulation(
                "reg-1",
                "Регламент вступления в закрытое сообщество",
                "Последовательность этапов, лимиты, пороги и правила отбора кандидатов",
                now(),
                "admin-1",
                defaultStages(),
                true
        );
        regulations.put(defaultRegulation.getId(), defaultRegulation);

        Invitation activeInvitation = new Invitation(
                "inv-seed-active",
                "bk-seed-active",
                "member-1",
                "Приглашение кандидата по рекомендации участника",
                now().minus(1, ChronoUnit.DAYS),
                now().plus(properties.getInvitationTtlDays() - 1L, ChronoUnit.DAYS),
                InvitationStatus.ACTIVE
        );
        invitations.put(activeInvitation.getId(), activeInvitation);

        Invitation activatedInvitation = new Invitation(
                "inv-seed-activated",
                "bk-seed-activated",
                "member-1",
                "Активированное приглашение",
                now().minus(5, ChronoUnit.DAYS),
                now().plus(25, ChronoUnit.DAYS),
                InvitationStatus.ACTIVATED
        );
        invitations.put(activatedInvitation.getId(), activatedInvitation);

        seedCandidate(
                "candidate-vote",
                "Петрова Мария Сергеевна",
                null,
                activatedInvitation.getId(),
                "task",
                CandidateStatus.VOTING
        );
        seedCandidate(
                "candidate-block",
                "Смирнова Анна Сергеевна",
                null,
                activatedInvitation.getId(),
                "interview",
                CandidateStatus.IN_PROGRESS
        );
        seedCandidate(
                "candidate-stage",
                "Кузнецов Иван Андреевич",
                "candidate-user-1",
                activatedInvitation.getId(),
                "task",
                CandidateStatus.IN_PROGRESS
        );

        Candidate votingCandidate = candidates.get("candidate-vote");
        VotingSession session = createVotingSession("admin-1", votingCandidate.getCurrentStageId(), 60);
        votingCandidate.getVotingSessions().add(session);
    }

    private void seedCandidate(
            String id,
            String fullName,
            String candidateUserId,
            String invitationId,
            String currentStageId,
            CandidateStatus status
    ) {
        Candidate candidate = new Candidate(
                id,
                fullName,
                candidateUserId,
                invitationId,
                "member-1",
                now().minus(3, ChronoUnit.DAYS),
                status,
                currentStageId
        );
        for (SelectionStage stage : activeRegulation().getStages()) {
            StageState state;
            if (stage.id().equals(currentStageId)) {
                state = status == CandidateStatus.VOTING ? StageState.PASSED : StageState.AVAILABLE;
            } else if (stageOrder(stage.id()) < stageOrder(currentStageId)) {
                state = StageState.PASSED;
            } else {
                state = StageState.WAITING;
            }
            candidate.getStages().add(progressFrom(stage, state, 1));
        }
        assignSeedInterviewer(candidate);
        candidates.put(candidate.getId(), candidate);
    }

    private List<SelectionStage> defaultStages() {
        return List.of(
                new SelectionStage("form", "Анкета кандидата", StageType.FORM, 1, 7, null, "Заполненная анкета", true),
                new SelectionStage("task", "Первое испытание", StageType.TASK, 2, 7, 70, "Результат задания не ниже порога", true),
                new SelectionStage(
                        "interview",
                        "Интервью с наставником",
                        StageType.INTERVIEW,
                        1,
                        5,
                        60,
                        "Отчет интервьюера и голоса участников",
                        false
                )
        );
    }

    private StageProgress progressFrom(SelectionStage stage, StageState state, int attemptNumber) {
        stageProgressSequence++;
        return new StageProgress(
                "stage-progress-" + stageProgressSequence,
                stage.id(),
                stage.name(),
                stage.type(),
                stage.attemptLimit(),
                state,
                attemptNumber
        );
    }

    private void assignSeedInterviewer(Candidate candidate) {
        StageProgress current = requireCurrentStage(candidate);
        Optional<UserAccount> demoInterviewer = userDirectory.findById("interviewer-1")
                .filter(user -> user.hasRole(Role.MEMBER));
        if (demoInterviewer.isPresent()) {
            current.assignInterviewer(demoInterviewer.get().getId());
            return;
        }
        assignRandomInterviewer(candidate, current);
    }

    private void assignRandomInterviewer(Candidate candidate, StageProgress progress) {
        progress.assignInterviewer(selectRandomInterviewerUserId(candidate));
    }

    private String selectRandomInterviewerUserId(Candidate candidate) {
        List<UserAccount> eligibleMembers = eligibleInterviewers(candidate, true);
        if (eligibleMembers.isEmpty()) {
            eligibleMembers = eligibleInterviewers(candidate, false);
        }
        if (eligibleMembers.isEmpty()) {
            throw new BusinessRuleException(
                    "INTERVIEWER_NOT_AVAILABLE",
                    "No active community members available for interviewer assignment"
            );
        }
        return eligibleMembers.get(secureRandom.nextInt(eligibleMembers.size())).getId();
    }

    private List<UserAccount> eligibleInterviewers(Candidate candidate, boolean avoidConflicts) {
        return userDirectory.listUsers().stream()
                .filter(user -> user.hasRole(Role.MEMBER))
                .filter(user -> !user.getId().equals(candidate.getCandidateUserId()))
                .filter(user -> !avoidConflicts || !user.getId().equals(candidate.getInvitedByUserId()))
                .filter(user -> !avoidConflicts || !user.hasRole(Role.ADMIN))
                .sorted(Comparator.comparing(UserAccount::getId))
                .toList();
    }

    private Map<String, Object> stageAssignedDetails(SelectionStage stage, StageProgress progress) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("stageName", stage.name());
        details.put("interviewerUserId", progress.getAssignedInterviewerUserId());
        return details;
    }

    private VotingSession createVotingSession(String actorUserId, String stageId, int thresholdPercent) {
        votingSessionSequence++;
        return new VotingSession(
                "voting-" + votingSessionSequence,
                stageId,
                actorUserId,
                now(),
                now().plus(3, ChronoUnit.DAYS),
                thresholdPercent
        );
    }

    private VotingSession openVotingForCurrentStage(Candidate candidate, String actorUserId) {
        Optional<VotingSession> existing = candidate.openVotingSession();
        if (existing.isPresent()) {
            return existing.get();
        }

        StageProgress current = requireCurrentStage(candidate);
        if (current.getState() != StageState.PASSED) {
            throw new BusinessRuleException("STAGE_NOT_READY_FOR_VOTING", "Этап должен быть пройден до открытия голосования");
        }

        SelectionStage stage = requireActiveStage(current.getStageId());
        int threshold = stage.thresholdPercent() != null ? stage.thresholdPercent() : 60;
        candidate.setStatus(CandidateStatus.VOTING);
        VotingSession session = createVotingSession(actorUserId, current.getStageId(), threshold);
        candidate.getVotingSessions().add(session);
        event(EventType.VOTE_OPENED, actorUserId, candidate.getId(), session.getId(), Map.of("thresholdPercent", threshold));
        return session;
    }

    private void advanceCandidate(Candidate candidate, String actorUserId) {
        List<SelectionStage> stages = activeRegulation().getStages();
        int currentIndex = -1;
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).id().equals(candidate.getCurrentStageId())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0 || currentIndex == stages.size() - 1) {
            candidate.setStatus(CandidateStatus.PASSED);
            promoteCandidateToMember(candidate, actorUserId);
            return;
        }

        SelectionStage nextStage = stages.get(currentIndex + 1);
        candidate.setCurrentStageId(nextStage.id());
        StageProgress nextProgress = candidate.getStages().stream()
                .filter(progress -> progress.getStageId().equals(nextStage.id()))
                .findFirst()
                .orElseGet(() -> {
                    StageProgress created = progressFrom(nextStage, StageState.WAITING, 1);
                    candidate.getStages().add(created);
                    return created;
                });
        nextProgress.setState(StageState.AVAILABLE);
        candidate.setStatus(nextStage.type() == StageType.VOTE ? CandidateStatus.VOTING : CandidateStatus.IN_PROGRESS);
        assignRandomInterviewer(candidate, nextProgress);
        event(
                EventType.STAGE_ASSIGNED,
                actorUserId,
                candidate.getId(),
                nextStage.id(),
                stageAssignedDetails(nextStage, nextProgress)
        );
        if (nextStage.type() == StageType.VOTE && candidate.openVotingSession().isEmpty()) {
            int thresholdPercent = nextStage.thresholdPercent() != null ? nextStage.thresholdPercent() : 60;
            VotingSession session = createVotingSession(actorUserId, nextStage.id(), thresholdPercent);
            candidate.getVotingSessions().add(session);
            event(
                    EventType.VOTE_OPENED,
                    actorUserId,
                    candidate.getId(),
                    session.getId(),
                    Map.of("thresholdPercent", session.getThresholdPercent())
            );
        }
    }

    private void ensureStageReadyForVerdict(StageProgress current, SelectionStage stage) {
        if (stage.requiresSubmission() && current.getState() != StageState.SUBMITTED) {
            throw new BusinessRuleException("STAGE_NOT_READY_FOR_VERDICT", "Сначала кандидат должен отправить результат этапа");
        }
        if (!stage.requiresSubmission()
                && current.getState() != StageState.AVAILABLE
                && current.getState() != StageState.RETRY) {
            throw new BusinessRuleException("STAGE_NOT_READY_FOR_VERDICT", "Этап уже обработан или ожидает другого действия");
        }
    }

    private void promoteCandidateToMember(Candidate candidate, String actorUserId) {
        promoteCandidateAccount(candidate, actorUserId);
        rewardInviterWithPrivilegedStatus(candidate, actorUserId);
    }

    private void promoteCandidateAccount(Candidate candidate, String actorUserId) {
        if (candidate.getCandidateUserId() == null) {
            return;
        }
        UserAccount account = userDirectory.findById(candidate.getCandidateUserId()).orElse(null);
        if (account == null) {
            return;
        }
        if (!account.hasRole(Role.MEMBER)) {
            userDirectory.assignRole(candidate.getCandidateUserId(), Role.MEMBER);
            event(
                    EventType.ROLE_ASSIGNED,
                    actorUserId,
                    candidate.getId(),
                    candidate.getCandidateUserId(),
                    Map.of("role", Role.MEMBER.name())
            );
        }
        if (account.hasRole(Role.CANDIDATE)) {
            userDirectory.revokeRole(candidate.getCandidateUserId(), Role.CANDIDATE);
            event(
                    EventType.ROLE_REVOKED,
                    actorUserId,
                    candidate.getId(),
                    candidate.getCandidateUserId(),
                    Map.of("role", Role.CANDIDATE.name())
            );
        }
    }

    private void rewardInviterWithPrivilegedStatus(Candidate candidate, String actorUserId) {
        if (candidate.getInvitedByUserId() == null) {
            return;
        }
        UserAccount inviter = userDirectory.findById(candidate.getInvitedByUserId()).orElse(null);
        if (inviter == null || inviter.hasRole(Role.PRIVILEGED_MEMBER)) {
            return;
        }
        userDirectory.assignRole(candidate.getInvitedByUserId(), Role.PRIVILEGED_MEMBER);
        event(
                EventType.ROLE_ASSIGNED,
                actorUserId,
                candidate.getId(),
                candidate.getInvitedByUserId(),
                Map.of("role", Role.PRIVILEGED_MEMBER.name(), "reason", "SUCCESSFUL_REFERRAL")
        );
    }

    private void validateRegulationStages(List<SelectionStage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new BusinessRuleException("REGULATION_EMPTY", "Регламент должен содержать хотя бы один этап");
        }
        List<String> stageIds = new ArrayList<>();
        for (SelectionStage stage : stages) {
            requireText(stage.id(), "stage.id", "Идентификатор этапа обязателен");
            requireText(stage.name(), "stage.name", "Название этапа обязательно");
            if (stageIds.contains(stage.id())) {
                throw new BusinessRuleException("REGULATION_DUPLICATE_STAGE", "Идентификаторы этапов регламента не должны повторяться");
            }
            stageIds.add(stage.id());
            if (stage.attemptLimit() < 1) {
                throw new BusinessRuleException("REGULATION_INVALID_ATTEMPT_LIMIT", "Лимит попыток этапа должен быть не меньше 1");
            }
            if (stage.dueDays() < 1) {
                throw new BusinessRuleException("REGULATION_INVALID_DUE_DAYS", "Срок выполнения этапа должен быть не меньше 1 дня");
            }
            if (stage.type() == null) {
                throw new BusinessRuleException("REGULATION_INVALID_STAGE_TYPE", "Тип этапа обязателен");
            }
            if (stage.thresholdPercent() != null && (stage.thresholdPercent() < 1 || stage.thresholdPercent() > 100)) {
                throw new BusinessRuleException("REGULATION_INVALID_THRESHOLD", "Порог этапа должен быть от 1 до 100 процентов");
            }
        }
    }

    private void ensureCandidateCanMove(Candidate candidate) {
        if (candidate.getStatus() == CandidateStatus.BLOCKED
                || candidate.getStatus() == CandidateStatus.PASSED
                || candidate.getStatus() == CandidateStatus.FAILED) {
            throw new BusinessRuleException("CANDIDATE_STATUS_LOCKED", "Кандидат не может продолжать отбор в текущем статусе");
        }
    }

    private void ensureCandidateOwnsRecord(UserAccount actor, Candidate candidate) {
        if (!actor.getId().equals(candidate.getCandidateUserId())) {
            throw new BusinessRuleException("ACCESS_DENIED", "Недостаточно прав для выполнения действия");
        }
    }

    private void requireAssignedInterviewerOrAdmin(UserAccount actor, StageProgress current) {
        if (actor.hasRole(Role.ADMIN) || actor.getId().equals(current.getAssignedInterviewerUserId())) {
            return;
        }
        throw new BusinessRuleException("ACCESS_DENIED", "Недостаточно прав для выполнения действия");
    }

    private boolean isCurrentStageAssignedTo(Candidate candidate, String userId) {
        return candidate.currentStage()
                .map(StageProgress::getAssignedInterviewerUserId)
                .map(userId::equals)
                .orElse(false);
    }

    private StageProgress requireCurrentStage(Candidate candidate) {
        return candidate.currentStage()
                .orElseThrow(() -> new BusinessRuleException("CURRENT_STAGE_NOT_FOUND", "Текущий этап кандидата не найден"));
    }

    private SelectionStage requireActiveStage(String stageId) {
        return activeRegulation().getStages().stream()
                .filter(stage -> stage.id().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("STAGE_NOT_FOUND", "Этап не найден в активном регламенте"));
    }

    private SelectionRegulation activeRegulation() {
        return regulations.values().stream()
                .filter(SelectionRegulation::isActive)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("ACTIVE_REGULATION_NOT_FOUND", "Активный регламент не найден"));
    }

    private int stageOrder(String stageId) {
        List<SelectionStage> stages = activeRegulation().getStages();
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).id().equals(stageId)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private UserAccount requireRole(String actorUserId, Role role) {
        UserAccount user = requireUser(actorUserId);
        if (!user.hasRole(role)) {
            throw new BusinessRuleException("ACCESS_DENIED", "Недостаточно прав для выполнения действия");
        }
        return user;
    }

    private UserAccount requireUser(String userId) {
        return userDirectory.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "Пользователь не найден"));
    }

    private Invitation requireInvitation(String invitationId) {
        Invitation invitation = invitations.get(invitationId);
        if (invitation == null) {
            throw new BusinessRuleException("INVITATION_NOT_FOUND", "Приглашение не найдено");
        }
        return invitation;
    }

    private Candidate requireCandidate(String candidateId) {
        Candidate candidate = candidates.get(candidateId);
        if (candidate == null) {
            throw new BusinessRuleException("CANDIDATE_NOT_FOUND", "Кандидат не найден");
        }
        return candidate;
    }

    private void event(EventType type, String actorUserId, String candidateId, String aggregateId, Map<String, Object> details) {
        eventSequence++;
        EventRecord event = new EventRecord(
                "event-" + UUID.randomUUID(),
                type,
                actorUserId,
                candidateId,
                aggregateId,
                details,
                now()
        );
        events.add(event);
        stateRepository.recordEvent(event);
        stateRepository.replaceSnapshot(snapshot());
    }

    private String generatePassword() {
        byte[] bytes = new byte[9];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private void requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("VALIDATION_" + field.toUpperCase().replace('.', '_'), message);
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
