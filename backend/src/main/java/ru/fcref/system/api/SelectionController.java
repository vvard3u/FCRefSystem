package ru.fcref.system.api;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.fcref.system.domain.BlockRecord;
import ru.fcref.system.domain.Candidate;
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
import ru.fcref.system.service.SelectionService;
import ru.fcref.system.service.SelectionSnapshot;

@RestController
@RequestMapping("/api")
public class SelectionController {

    private final SelectionService service;

    public SelectionController(SelectionService service) {
        this.service = service;
    }

    @GetMapping("/snapshot")
    public SelectionSnapshot snapshot() {
        return service.snapshot();
    }

    @PostMapping("/invitations")
    public Invitation createInvitation(@RequestBody CreateInvitationRequest request) {
        return service.createInvitation(request.actorUserId(), request.comment(), request.requestId());
    }

    @PostMapping("/invitations/activate")
    public Candidate activateInvitation(@RequestBody ActivateInvitationRequest request) {
        return service.activateInvitation(request.token(), request.fullName());
    }

    @PostMapping("/regulations")
    public SelectionRegulation createRegulation(@RequestBody CreateRegulationRequest request) {
        return service.createRegulation(request.actorUserId(), request.name(), request.description(), request.stages());
    }

    @PostMapping("/candidates/{candidateId}/stage-results")
    public StageProgress submitStageResult(
            @PathVariable String candidateId,
            @RequestBody SubmitStageResultRequest request
    ) {
        return service.submitStageResult(request.actorUserId(), candidateId, request.result(), request.requestId());
    }

    @PostMapping("/candidates/{candidateId}/verdicts")
    public StageProgress recordVerdict(
            @PathVariable String candidateId,
            @RequestBody RecordVerdictRequest request
    ) {
        return service.recordVerdict(request.actorUserId(), candidateId, request.verdict(), request.report());
    }

    @PostMapping("/candidates/{candidateId}/voting")
    public VotingSession openVote(
            @PathVariable String candidateId,
            @RequestBody ActorRequest request
    ) {
        return service.openVote(request.actorUserId(), candidateId);
    }

    @PostMapping("/candidates/{candidateId}/votes")
    public Vote castVote(
            @PathVariable String candidateId,
            @RequestBody CastVoteRequest request
    ) {
        return service.castVote(request.actorUserId(), candidateId, request.choice(), request.reason());
    }

    @PostMapping("/candidates/{candidateId}/voting/close")
    public VotingSession closeVote(
            @PathVariable String candidateId,
            @RequestBody ActorRequest request
    ) {
        return service.closeVote(request.actorUserId(), candidateId);
    }

    @PostMapping("/candidates/{candidateId}/complaints")
    public Complaint createComplaint(
            @PathVariable String candidateId,
            @RequestBody ComplaintRequest request
    ) {
        return service.createComplaint(request.actorUserId(), candidateId, request.reason());
    }

    @PostMapping("/candidates/{candidateId}/blocks")
    public BlockRecord blockCandidate(
            @PathVariable String candidateId,
            @RequestBody BlockCandidateRequest request
    ) {
        return service.blockCandidate(request.actorUserId(), candidateId, request.category(), request.reason());
    }

    @PostMapping("/candidates/{candidateId}/unblock")
    public BlockRecord unblockCandidate(
            @PathVariable String candidateId,
            @RequestBody UnblockCandidateRequest request
    ) {
        return service.unblockCandidate(request.actorUserId(), candidateId, request.reason());
    }

    @PostMapping("/users/{userId}/roles")
    public UserAccount assignRole(
            @PathVariable String userId,
            @RequestBody RoleChangeRequest request
    ) {
        return service.assignRole(request.actorUserId(), userId, request.role());
    }

    @DeleteMapping("/users/{userId}/roles/{role}")
    public UserAccount revokeRole(
            @PathVariable String userId,
            @PathVariable Role role,
            @RequestParam String actorUserId
    ) {
        return service.revokeRole(actorUserId, userId, role);
    }

    public record ActorRequest(String actorUserId) {
    }

    public record CreateInvitationRequest(String actorUserId, String comment, String requestId) {
    }

    public record ActivateInvitationRequest(String token, String fullName) {
    }

    public record CreateRegulationRequest(
            String actorUserId,
            String name,
            String description,
            List<SelectionStage> stages
    ) {
    }

    public record SubmitStageResultRequest(String actorUserId, String result, String requestId) {
    }

    public record RecordVerdictRequest(String actorUserId, Verdict verdict, String report) {
    }

    public record CastVoteRequest(String actorUserId, VoteChoice choice, String reason) {
    }

    public record ComplaintRequest(String actorUserId, String reason) {
    }

    public record BlockCandidateRequest(String actorUserId, String category, String reason) {
    }

    public record UnblockCandidateRequest(String actorUserId, String reason) {
    }

    public record RoleChangeRequest(String actorUserId, Role role) {
    }
}
