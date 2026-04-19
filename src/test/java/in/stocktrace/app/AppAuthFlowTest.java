package in.stocktrace.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end HTTP test of the register -&gt; login -&gt; me flow plus
 * admin-only access control. Uses the H2 test profile so no network or
 * external dependency is required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppAuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AppUserService service;

    @BeforeEach
    void wipe() {
        service.listAll().forEach(u -> { /* keep simple — relies on test DB being ephemeral */ });
    }

    @Test
    void registerLoginRequiresActivation() throws Exception {
        String email = "alice+" + System.nanoTime() + "@example.com";
        String body = mapper.writeValueAsString(new AppUserDto.RegisterRequest(
                email, "supersecret123", "Alice", null));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.active", is(false)));

        // login while inactive -> 403
        String login = mapper.writeValueAsString(new AppUserDto.LoginRequest(email, "supersecret123"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isForbidden());

        // activate and login succeeds
        AppUser u = service.findByEmail(email).orElseThrow();
        service.setActive(u.getId(), true);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is(notNullValue())))
                .andExpect(jsonPath("$.user.email", is(email)));
    }

    @Test
    void meRequiresJwtAndAdminEndpointRequiresRole() throws Exception {
        String email = "bob+" + System.nanoTime() + "@example.com";
        service.create(email, "supersecret123", "Bob", AppRole.USER, true);
        String login = mapper.writeValueAsString(new AppUserDto.LoginRequest(email, "supersecret123"));
        String resp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(resp).get("token").asText();

        // /me with no token -> 401
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        // /me with token -> 200
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)));

        // /api/admin/users with user token -> 403
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Promote and retry -> 200 (reissue token to pick up the new role)
        AppUser u = service.findByEmail(email).orElseThrow();
        service.setRole(u.getId(), AppRole.ADMIN);
        resp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String adminToken = mapper.readTree(resp).get("token").asText();

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
