package ru.fcref.system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "debug=false",
        "logging.level.org.springframework=INFO",
        "spring.datasource.url=jdbc:h2:mem:fcref-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.data-locations=classpath:data-h2.sql"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SelectionControllerTest {

    private static final String ADMIN_AUTH = basic("admin", "admin");
    private static final String MEMBER_AUTH = basic("member", "member");
    private static final String PRIVILEGED_AUTH = basic("privileged", "privileged");
    private static final String PRIVILEGED2_AUTH = basic("privileged2", "privileged2");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectedApiRequiresBasicAuth() throws Exception {
        mockMvc.perform(get("/api/snapshot"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicActivationPageIsAvailableWithoutBasicAuth() throws Exception {
        mockMvc.perform(get("/activate.html"))
                .andExpect(status().isOk());
    }

    @Test
    void adminSnapshotReturnsCleanMvpState() throws Exception {
        mockMvc.perform(get("/api/snapshot").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(4))
                .andExpect(jsonPath("$.candidates.length()").value(0))
                .andExpect(jsonPath("$.invitations.length()").value(0))
                .andExpect(jsonPath("$.regulations[0].active").value(true));
    }

    @Test
    void cleanSelectionStateIsPersistedForDatabaseInspection() {
        Integer userCount = jdbcTemplate.queryForObject("select count(*) from app_users", Integer.class);
        Integer candidateCount = jdbcTemplate.queryForObject("select count(*) from candidates", Integer.class);
        Integer stageCount = jdbcTemplate.queryForObject("select count(*) from candidate_stage_progress", Integer.class);
        Integer votingCount = jdbcTemplate.queryForObject("select count(*) from voting_sessions", Integer.class);

        assertThat(userCount).isEqualTo(4);
        assertThat(candidateCount).isZero();
        assertThat(stageCount).isZero();
        assertThat(votingCount).isZero();
    }

    @Test
    void candidateSnapshotContainsOnlyOwnCandidateFlow() throws Exception {
        ActivatedCandidate candidate = activateCandidateThroughApi();

        mockMvc.perform(get("/api/snapshot").header(HttpHeaders.AUTHORIZATION, basic(candidate.username(), candidate.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations.length()").value(0))
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].id").value(candidate.candidateId()));
    }

    @Test
    void memberSnapshotDoesNotExposeRegulationManagementData() throws Exception {
        mockMvc.perform(get("/api/snapshot").header(HttpHeaders.AUTHORIZATION, MEMBER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations").isArray())
                .andExpect(jsonPath("$.regulations.length()").value(1));
    }

    @Test
    void sessionReturnsAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/session").header(HttpHeaders.AUTHORIZATION, MEMBER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("member"))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"));
    }

    @Test
    void memberCanCreateInvitationThroughApi() throws Exception {
        mockMvc.perform(post("/api/invitations")
                        .header(HttpHeaders.AUTHORIZATION, MEMBER_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "API test invitation",
                                  "requestId": "api-test-invitation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void publicActivationCreatesCandidateCredentials() throws Exception {
        ActivatedCandidate candidate = activateCandidateThroughApi();
        Integer privilegedRoleCount = jdbcTemplate.queryForObject(
                "select count(*) from app_user_roles where user_id = ? and role = 'PRIVILEGED_MEMBER'",
                Integer.class,
                candidate.assignedInterviewerUserId()
        );

        assertThat(candidate.username()).startsWith("candidate");
        assertThat(candidate.password()).isNotBlank();
        assertThat(candidate.assignedInterviewerUserId()).isNotBlank();
        assertThat(privilegedRoleCount).isEqualTo(1);
    }

    @Test
    void apiMapsBusinessRuleToForbidden() throws Exception {
        mockMvc.perform(post("/api/candidates/candidate-any/votes")
                        .header(HttpHeaders.AUTHORIZATION, MEMBER_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "choice": "SUPPORT",
                                  "reason": "member has no privileged role"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void privilegedMemberCanVoteThroughApi() throws Exception {
        ActivatedCandidate candidate = activateCandidateThroughApi();
        submitStageResult(candidate);
        recordAssignedVerdict(candidate);

        mockMvc.perform(post("/api/candidates/" + candidate.candidateId() + "/votes")
                        .header(HttpHeaders.AUTHORIZATION, PRIVILEGED_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "choice": "SUPPORT",
                                  "reason": "candidate meets voting criteria"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choice").value("SUPPORT"));
    }

    private ActivatedCandidate activateCandidateThroughApi() throws Exception {
        String token = createInvitationToken();
        MvcResult result = mockMvc.perform(post("/api/invitations/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "fullName": "Public Candidate"
                                }
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.username", notNullValue()))
                .andExpect(jsonPath("$.password", notNullValue()))
                .andExpect(jsonPath("$.candidate.stages[0].assignedInterviewerUserId", notNullValue()))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new ActivatedCandidate(
                response.at("/candidate/id").asText(),
                response.at("/candidate/candidateUserId").asText(),
                response.at("/username").asText(),
                response.at("/password").asText(),
                response.at("/candidate/stages/0/assignedInterviewerUserId").asText()
        );
    }

    private String createInvitationToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/invitations")
                        .header(HttpHeaders.AUTHORIZATION, MEMBER_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "API activation invitation",
                                  "requestId": "api-activation-invitation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/token").asText();
    }

    private void submitStageResult(ActivatedCandidate candidate) throws Exception {
        mockMvc.perform(post("/api/candidates/" + candidate.candidateId() + "/stage-results")
                        .header(HttpHeaders.AUTHORIZATION, basic(candidate.username(), candidate.password()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "API submitted result",
                                  "requestId": "api-stage-result"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUBMITTED"));
    }

    private void recordAssignedVerdict(ActivatedCandidate candidate) throws Exception {
        mockMvc.perform(post("/api/candidates/" + candidate.candidateId() + "/verdicts")
                        .header(HttpHeaders.AUTHORIZATION, authForUserId(candidate.assignedInterviewerUserId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verdict": "PASSED",
                                  "report": "API accepted result"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PASSED"));
    }

    private static String authForUserId(String userId) {
        return switch (userId) {
            case "privileged-1" -> PRIVILEGED_AUTH;
            case "privileged-2" -> PRIVILEGED2_AUTH;
            default -> throw new IllegalArgumentException("Unknown demo privileged user: " + userId);
        };
    }

    private static String basic(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private record ActivatedCandidate(
            String candidateId,
            String candidateUserId,
            String username,
            String password,
            String assignedInterviewerUserId
    ) {
    }
}
