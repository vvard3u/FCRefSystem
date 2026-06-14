package ru.fcref.system.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "debug=false",
        "logging.level.org.springframework=INFO"
})
@AutoConfigureMockMvc
class SelectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void snapshotReturnsSeededMvpState() throws Exception {
        mockMvc.perform(get("/api/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.regulations[0].active").value(true));
    }

    @Test
    void memberCanCreateInvitationThroughApi() throws Exception {
        mockMvc.perform(post("/api/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorUserId": "member-1",
                                  "comment": "API test invitation",
                                  "requestId": "api-test-invitation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void apiMapsBusinessRuleToConflict() throws Exception {
        mockMvc.perform(post("/api/candidates/candidate-vote/votes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorUserId": "member-1",
                                  "choice": "SUPPORT",
                                  "reason": "member has no privileged role"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
