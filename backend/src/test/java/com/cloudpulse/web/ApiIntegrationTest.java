package com.cloudpulse.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end API tests on an in-memory H2 database (scheduler disabled). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private static String service(String name, String url, int interval) {
        return "{\"name\":\"" + name + "\",\"url\":\"" + url + "\",\"provider\":\"RENDER\","
                + "\"checkIntervalSeconds\":" + interval + ",\"timeoutMs\":5000,\"failureThreshold\":2}";
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/services")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/services").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metadataAndNonHttpTargetsAreRejected() throws Exception {
        String auth = token();
        mvc.perform(post("/api/services").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(service("evil-1", "http://169.254.169.254/latest/meta-data/", 60)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.url", notNullValue()));
        mvc.perform(post("/api/services").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(service("evil-2", "file:///etc/passwd", 60)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesNumericBounds() throws Exception {
        mvc.perform(post("/api/services").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                        .content(service("too-fast", "http://127.0.0.1:9/health", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.checkIntervalSeconds", notNullValue()));
    }

    @Test
    void serviceLifecycle() throws Exception {
        String auth = token();
        String created = mvc.perform(post("/api/services").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(service("Lifecycle API", "http://127.0.0.1:9/health", 30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("UNKNOWN")))
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.recent", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        // duplicate name
        mvc.perform(post("/api/services").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(service("lifecycle api", "http://127.0.0.1:9/other", 30)))
                .andExpect(status().isConflict());

        // disable
        mvc.perform(patch("/api/services/" + id + "/enabled").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled", is(false)));

        // no data yet: unknown values stay null instead of being invented
        mvc.perform(get("/api/services/" + id + "/latency").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalChecks", is(0)))
                .andExpect(jsonPath("$.stats.availabilityPercent").doesNotExist());

        mvc.perform(get("/api/summary").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generatedAt", notNullValue()));

        mvc.perform(delete("/api/services/" + id).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(get("/api/services/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
    }
}
