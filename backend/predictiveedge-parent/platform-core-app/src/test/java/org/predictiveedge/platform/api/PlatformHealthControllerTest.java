package org.predictiveedge.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.predictiveedge.identity.application.IdentityService;
import org.predictiveedge.identity.api.PlatformSecurityConfiguration;
import org.predictiveedge.identity.domain.UserAccount;
import org.predictiveedge.identity.domain.UserState;

@WebMvcTest(PlatformHealthController.class)
@Import(PlatformSecurityConfiguration.class)
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
class PlatformHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void healthIsPublicAndReportsTheService() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("predictiveedge-platform-core"));
    }

    @Test
    void chartIntelligenceApiIsAvailableToAuthenticatedUsers() throws Exception {
        var user = new UserAccount(UUID.randomUUID(), "analyst@example.com", "+919999999999", "Analyst",
                UserState.ACTIVE, Instant.now(), Instant.now());
        when(identityService.authenticate("test-token"))
                .thenReturn(new IdentityService.AuthenticatedIdentity(UUID.randomUUID(), user));

        // No controller is loaded in this MVC slice, so 404 proves security allowed the request through.
        mockMvc.perform(post("/api/chart-intelligence/v1/security-probe")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound());
    }
}
