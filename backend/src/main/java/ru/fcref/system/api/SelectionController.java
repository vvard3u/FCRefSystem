package ru.fcref.system.api;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.fcref.system.domain.ActivationResult;
import ru.fcref.system.domain.BlockRecord;
import ru.fcref.system.domain.Complaint;
import ru.fcref.system.domain.Invitation;
import ru.fcref.system.domain.Role;
import ru.fcref.system.domain.SelectionRegulation;
import ru.fcref.system.domain.SelectionStage;
import ru.fcref.system.domain.StageProgress;
import ru.fcref.system.domain.UserAccount;
import ru.fcref.system.domain.Verdict;
import ru.fcref.system.domain.Vote;
import ru.fcref.system.domain.VoteChoice;
import ru.fcref.system.domain.VotingSession;
import ru.fcref.system.service.CurrentUserService;
import ru.fcref.system.service.SelectionService;
import ru.fcref.system.service.SelectionSnapshot;

@RestController
@RequestMapping("/api")
public class SelectionController {

    private final SelectionService service;
    private final CurrentUserService currentUserService;

    public SelectionController(SelectionService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/session")
    public UserAccount session(Authentication authentication) {
        return currentUserService.requireCurrent(authentication);
    }

    @GetMapping("/snapshot")
    public SelectionSnapshot snapshot(Authentication authentication) {
        return service.snapshotFor(currentUserService.requireCurrent(authentication));
    }

    @PostMapping("/invitations")
    public Invitation createInvitation(@RequestBody CreateInvitationRequest request, Authentication authentication) {
        return service.createInvitation(actorUserId(authentication), request.comment(), request.requestId());
    }

    @PostMapping("/invitations/activate")
    public ActivationResult activateInvitation(@RequestBody ActivateInvitationRequest request) {
        return service.activateInvitation(request.token(), request.fullName());
    }

    @PostMapping("/regulations")
    public SelectionRegulation createRegulation(
            @RequestBody CreateRegulationRequest request,
            Authentication authentication
    ) {
        return service.createRegulation(actorUserId(authentication), request.name(), request.description(), request.stages());
    }

    @PostMapping("/candidates/{candidateId}/stage-results")
    public StageProgress submitStageResult(
            @PathVariable String candidateId,
            @RequestBody SubmitStageResultRequest request,
            Authentication authentication
    ) {
        return service.submitStageResult(actorUserId(authentication), candidateId, request.result(), request.requestId());
    }

    @PostMapping("/candidates/{candidateId}/verdicts")
    public StageProgress recordVerdict(
            @PathVariable String candidateId,
            @RequestBody RecordVerdictRequest request,
            Authentication authentication
    ) {
        return service.recordVerdict(actorUserId(authentication), candidateId, request.verdict(), request.report());
    }

    @PostMapping("/candidates/{candidateId}/voting")
    public VotingSession openVote(
            @PathVariable String candidateId,
            Authentication authentication
    ) {
        return service.openVote(actorUserId(authentication), candidateId);
    }

    @PostMapping("/candidates/{candidateId}/votes")
    public Vote castVote(
            @PathVariable String candidateId,
            @RequestBody CastVoteRequest request,
            Authentication authentication
    ) {
        return service.castVote(actorUserId(authentication), candidateId, request.choice(), request.reason());
    }

    @PostMapping("/candidates/{candidateId}/voting/close")
    public VotingSession closeVote(
            @PathVariable String candidateId,
            Authentication authentication
    ) {
        return service.closeVote(actorUserId(authentication), candidateId);
    }

    @PostMapping("/candidates/{candidateId}/complaints")
    public Complaint createComplaint(
            @PathVariable String candidateId,
            @RequestBody ComplaintRequest request,
            Authentication authentication
    ) {
        return service.createComplaint(actorUserId(authentication), candidateId, request.reason());
    }

    @PostMapping("/candidates/{candidateId}/blocks")
    public BlockRecord blockCandidate(
            @PathVariable String candidateId,
            @RequestBody BlockCandidateRequest request,
            Authentication authentication
    ) {
        return service.blockCandidate(actorUserId(authentication), candidateId, request.category(), request.reason());
    }

    @PostMapping("/candidates/{candidateId}/unblock")
    public BlockRecord unblockCandidate(
            @PathVariable String candidateId,
            @RequestBody UnblockCandidateRequest request,
            Authentication authentication
    ) {
        return service.unblockCandidate(actorUserId(authentication), candidateId, request.reason());
    }

    @PostMapping("/users/{userId}/roles")
    public UserAccount assignRole(
            @PathVariable String userId,
            @RequestBody RoleChangeRequest request,
            Authentication authentication
    ) {
        return service.assignRole(actorUserId(authentication), userId, request.role());
    }

    @DeleteMapping("/users/{userId}/roles/{role}")
    public UserAccount revokeRole(
            @PathVariable String userId,
            @PathVariable Role role,
            Authentication authentication
    ) {
        return service.revokeRole(actorUserId(authentication), userId, role);
    }

    private String actorUserId(Authentication authentication) {
        return currentUserService.requireCurrent(authentication).getId();
    }

    public record CreateInvitationRequest(String comment, String requestId) {
    }

    public record ActivateInvitationRequest(String token, String fullName) {
    }

    public record CreateRegulationRequest(
            String name,
            String description,
            List<SelectionStage> stages
    ) {
    }

    public record SubmitStageResultRequest(String result, String requestId) {
    }

    public record RecordVerdictRequest(Verdict verdict, String report) {
    }

    public record CastVoteRequest(VoteChoice choice, String reason) {
    }

    public record ComplaintRequest(String reason) {
    }

    public record BlockCandidateRequest(String category, String reason) {
    }

    public record UnblockCandidateRequest(String reason) {
    }

    public record RoleChangeRequest(Role role) {
    }
}
