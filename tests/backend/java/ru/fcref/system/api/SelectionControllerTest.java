package ru.fcref.system.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
class SelectionControllerTest {

    private static final String ADMIN_AUTH = basic("admin", "admin");
    private static final String CANDIDATE_AUTH = basic("candidate", "candidate");
    private static final String MEMBER_AUTH = basic("member", "member");
    private static final String PRIVILEGED_AUTH = basic("privileged", "privileged");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void adminSnapshotReturnsFullSeededMvpState() throws Exception {
        mockMvc.perform(get("/api/snapshot").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.regulations[0].active").value(true));
    }

    @Test
    void seededSelectionStateIsPersistedForDatabaseInspection() {
        Integer candidateCount = jdbcTemplate.queryForObject("select count(*) from candidates", Integer.class);
        Integer stageCount = jdbcTemplate.queryForObject("select count(*) from candidate_stage_progress", Integer.class);
        Integer votingCount = jdbcTemplate.queryForObject("select count(*) from voting_sessions", Integer.class);
        Integer assignedStageCount = jdbcTemplate.queryForObject(
                "select count(*) from candidate_stage_progress where assigned_interviewer_user_id is not null",
                Integer.class
        );

        assertThat(candidateCount).isGreaterThan(0);
        assertThat(stageCount).isGreaterThan(0);
        assertThat(assignedStageCount).isGreaterThan(0);
        assertThat(votingCount).isGreaterThan(0);
    }

    @Test
    void candidateSnapshotContainsOnlyOwnCandidateFlow() throws Exception {
        mockMvc.perform(get("/api/snapshot").header(HttpHeaders.AUTHORIZATION, CANDIDATE_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations.length()").value(0))
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].id").value("candidate-stage"));
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
        mockMvc.perform(post("/api/invitations/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "bk-seed-active",
                                  "fullName": "Public Candidate"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.username", notNullValue()))
                .andExpect(jsonPath("$.password", notNullValue()));
    }

    @Test
    void apiMapsBusinessRuleToForbidden() throws Exception {
        mockMvc.perform(post("/api/candidates/candidate-vote/votes")
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
        mockMvc.perform(post("/api/candidates/candidate-vote/votes")
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

    private static String basic(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
