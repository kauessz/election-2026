package br.com.election;

import br.com.election.poll.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PollApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired PollRepository polls;
    private static final String C1 = "00000000-0000-0000-0000-000000000001";
    private static final String C2 = "00000000-0000-0000-0000-000000000002";
    private UUID pollster;

    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM polls");
        jdbc.update("DELETE FROM pollster_performance");
        jdbc.update("DELETE FROM pollsters WHERE slug LIKE 'test-%'");
        jdbc.update("DELETE FROM candidates WHERE name = 'Teste'");
        pollster = jdbc.queryForObject("SELECT id FROM pollsters WHERE slug = 'datafolha'", UUID.class);
    }
    private String body(String basis, String first, String second) {
        return """
            {"pollsterId":"%s","fieldStart":"2026-10-18","fieldEnd":"2026-10-20",
             "publishedAt":"2026-10-22T15:00:00Z","sampleSize":2000,"marginOfError":2,
             "registrationNumber":"BR-12345/2026","methodology":"Entrevistas presenciais",
             "sourceUrl":"https://example.org/pesquisa","voteBasis":"%s",
             "results":[{"candidateId":"%s","originalPercentage":%s},{"candidateId":"%s","originalPercentage":%s}]}
            """.formatted(pollster, basis, C1, first, C2, second);
    }
    private UUID create(String body) throws Exception {
        String location = mvc.perform(post("/api/admin/polls").contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
    @Test void migrationsAndSeeds() {
        assertThat(flyway.info().applied()).hasSize(12);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidates", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pollsters", Integer.class)).isEqualTo(14);
    }
    @Test void createsCandidate() throws Exception {
        mvc.perform(post("/api/admin/candidates").contentType("application/json").content("""
            {"name":"Teste","displayName":"Teste","party":"XYZ","ballotNumber":99}
            """)).andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/candidates")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
    }
    @Test void validatesCandidate() throws Exception {
        mvc.perform(post("/api/admin/candidates").contentType("application/json").content("""
            {"name":"","displayName":"Teste","party":"XYZ","ballotNumber":0}
            """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
    }
    @Test void createsPollsterAndRejectsDuplicateSlug() throws Exception {
        String body = """ 
            {"name":"Teste","slug":"test-institute","websiteUrl":"https://example.org"}
            """;
        mvc.perform(post("/api/admin/pollsters").contentType("application/json").content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/pollsters").contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/pollsters")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(15)));
    }
    @Test void createsValidPoll() throws Exception {
        UUID id = create(body("VALID", "51.1", "48.9"));
        mvc.perform(get("/api/polls/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].normalizedPercentage").value(51.1));
    }
    @Test void createsTotalPoll() throws Exception {
        UUID id = create(body("TOTAL", "47", "45"));
        mvc.perform(get("/api/polls/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].normalizedPercentage").value(51.087))
            .andExpect(jsonPath("$.results[1].normalizedPercentage").value(48.913));
    }
    @Test void updateRecalculatesResults() throws Exception {
        UUID id = create(body("TOTAL", "47", "45"));
        mvc.perform(put("/api/admin/polls/" + id).contentType("application/json").content(body("TOTAL", "40", "40")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].normalizedPercentage").value(50));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poll_results WHERE poll_id = ?", Integer.class, id)).isEqualTo(2);
    }
    @Test void latestSelectsOnePerPollsterAndSorts() throws Exception {
        create(body("VALID", "51", "49").replace("22T15", "20T15"));
        UUID newest = create(body("VALID", "52", "48"));
        UUID quaest = jdbc.queryForObject("SELECT id FROM pollsters WHERE slug='quaest'", UUID.class);
        UUID other = create(body("VALID", "50", "50").replace(pollster.toString(), quaest.toString()).replace("22T15", "21T15"));
        mvc.perform(get("/api/polls/latest")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(newest.toString())).andExpect(jsonPath("$[1].id").value(other.toString()));
    }
    @Test void latestUsesFieldEndThenCreatedAtThenId() throws Exception {
        UUID olderField = create(body("VALID", "51", "49").replace("2026-10-20", "2026-10-19"));
        UUID olderCreated = create(body("VALID", "51", "49"));
        UUID newest = create(body("VALID", "52", "48"));
        jdbc.update("UPDATE polls SET created_at = '2026-10-20T00:00:00Z' WHERE id=?", olderCreated);
        jdbc.update("UPDATE polls SET created_at = '2026-10-21T00:00:00Z' WHERE id=?", newest);
        jdbc.update("UPDATE polls SET created_at = '2026-10-22T00:00:00Z' WHERE id=?", olderField);
        mvc.perform(get("/api/polls/latest")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(newest.toString()));
        jdbc.update("UPDATE polls SET created_at = '2026-10-21T00:00:00Z' WHERE id=?", olderCreated);
        String expected = jdbc.queryForObject("SELECT id::text FROM polls ORDER BY field_end DESC, created_at DESC, id DESC LIMIT 1", String.class);
        mvc.perform(get("/api/polls/latest")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(expected));
    }
    @Test void databaseRejectsDuplicateCandidate() throws Exception {
        UUID id = create(body("VALID", "51", "49"));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO poll_results(poll_id,candidate_id,original_percentage,normalized_percentage) VALUES (?,?::uuid,1,1)", id, C1))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void rejectsInvalidDates() throws Exception {
        mvc.perform(post("/api/admin/polls").contentType("application/json").content(body("TOTAL", "47", "45").replace("2026-10-20", "2026-10-17")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("fim do campo")));
    }
    @Test void rejectsInvalidPercentages() throws Exception {
        for (String value : new String[]{"-1", "101", "45.12345"}) {
            mvc.perform(post("/api/admin/polls").contentType("application/json").content(body("TOTAL", value, "0"))).andExpect(status().isBadRequest());
        }
    }
    @Test void rejectsZeroAndEmptyResults() throws Exception {
        mvc.perform(post("/api/admin/polls").contentType("application/json").content(body("TOTAL", "0", "0"))).andExpect(status().isBadRequest());
        String empty = body("TOTAL", "47", "45").replaceAll("\\[.*]", "[]");
        mvc.perform(post("/api/admin/polls").contentType("application/json").content(empty)).andExpect(status().isBadRequest());
    }
    @Test void rejectsDuplicateCandidateInRequest() throws Exception {
        mvc.perform(post("/api/admin/polls").contentType("application/json").content(body("TOTAL", "47", "45").replace(C2, C1))).andExpect(status().isBadRequest());
    }
    @Test void deletesPollAndResults() throws Exception {
        UUID id = create(body("VALID", "51", "49"));
        mvc.perform(delete("/api/admin/polls/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/polls/" + id)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poll_results WHERE poll_id=?", Integer.class, id)).isZero();
    }
    @Test void listsPollsByPublication() throws Exception {
        UUID old = create(body("VALID", "51", "49").replace("22T15", "21T15"));
        UUID newest = create(body("VALID", "52", "48"));
        mvc.perform(get("/api/polls")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(newest.toString())).andExpect(jsonPath("$[1].id").value(old.toString()));
    }
    @Test void invalidUpdateRollsBack() throws Exception {
        UUID id = create(body("TOTAL", "47", "45"));
        mvc.perform(put("/api/admin/polls/" + id).contentType("application/json").content(body("TOTAL", "0", "0"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/polls/" + id)).andExpect(jsonPath("$.results", hasSize(2))).andExpect(jsonPath("$.results[0].originalPercentage").value(47));
    }
    @Test void handlesMissingReferencesAndMalformedRequest() throws Exception {
        mvc.perform(post("/api/admin/polls").contentType("application/json").content(body("VALID", "51", "49").replace(pollster.toString(), UUID.randomUUID().toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/polls/invalid-id")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/polls").contentType("application/json").content("{")).andExpect(status().isBadRequest());
    }
    @Test void rejectsUnsafeSourceAndMissingRequiredFields() throws Exception {
        mvc.perform(post("/api/admin/polls").contentType("application/json").content(body("VALID", "51", "49").replace("https://example.org/pesquisa", "javascript:alert(1)"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/polls").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
    @Test void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/admin/polls").header("Origin", "http://localhost:3000").header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/admin/polls").header("Origin", "https://example.org").header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }
}
