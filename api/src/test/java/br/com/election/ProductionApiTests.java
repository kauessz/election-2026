package br.com.election;

import br.com.election.tse.*;
import br.com.election.candidate.CandidateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.List;
import static br.com.election.tse.TseTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.admin.api-key=test-only-key", "app.cors.allowed-origins=https://frontend.example.org,https://second.example.org"})
@AutoConfigureMockMvc @ActiveProfiles("production") @Import(TestcontainersConfiguration.class)
class ProductionApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OfficialSnapshotStore snapshots;
    @Autowired CandidateRepository candidates;
    @Autowired MunicipalityCatalogue catalogue;
    @Autowired TseResultsProperties configured;
    @Autowired OfficialResultsService service;
    @Autowired TseResultParser parser;
    @Test void adminRequiresHeaderAndDistinguishesWrongKey() throws Exception {
        mvc.perform(get("/api/admin/data-quality")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/data-quality").header("X-Admin-Key", "wrong-key")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/data-quality").header("X-Admin-Key", "test-only-key")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/polls/import?dryRun=true").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void protectsNestedAndSemicolonAdminPaths() throws Exception {
        for(var path:List.of("/api/admin/polls", "/api/admin/aggregates/snapshot", "/api/admin/pollster-performances/import", "/api/admin/poll-history/import", "/api/admin/poll-discoveries/sync"))
            mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/data-quality;extra")).andExpect(status().isUnauthorized());
    }
    @Test void allPublicReadEndpointsRemainPublic() throws Exception {
        for(var path:List.of("/api/candidates", "/api/pollsters", "/api/polls", "/api/polls/latest", "/api/poll-history", "/api/aggregates", "/api/aggregates/audit", "/api/election/source-status", "/api/election/president", "/api/election/president/projection"))
            mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get("/api/pre-election-freeze")).andExpect(status().isNoContent());
    }
    @Test void healthIsAnonymousAndDoesNotExposeDatabaseDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(14);
    }
    @Test void corsAllowsExactCsvOriginsAndAdminPreflightWithoutSecret() throws Exception {
        for (var origin:List.of("https://frontend.example.org", "https://second.example.org"))
            mvc.perform(get("/api/candidates").header("Origin", origin)).andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
        mvc.perform(options("/api/admin/polls/import").header("Origin", "https://frontend.example.org")
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "X-Admin-Key, Content-Type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.org"))
            .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Admin-Key")))
            .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
    @Test void corsRejectsForeignOriginAndPreflight() throws Exception {
        mvc.perform(get("/api/candidates").header("Origin", "https://attacker.example.org")).andExpect(status().isForbidden());
        mvc.perform(options("/api/admin/polls/import").header("Origin", "https://attacker.example.org")
            .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }
    @Test void restartRecoversStaleOfficialDataWithoutCallingTse() {
        var fixed=Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"),ZoneOffset.UTC);
        var stored=parser.parse(fixture("ea20-national-official.json"),OfficialElectionResult.Scope.national());
        snapshots.save(stored,fixed.instant().minusSeconds(600),null);
        var client=mock(TseResultsClient.class);
        var recovered=new OfficialResultsService(client,parser,new TseUrlBuilder(configured),snapshots,candidates,catalogue,configured,fixed);
        try {
            recovered.recoverAtStartup();
            var response=recovered.getPresidentNationalResult();
            assertThat(response.result()).isEqualTo(stored);
            assertThat(response.status()).isEqualTo(OfficialDataStatus.STALE);
            verifyNoInteractions(client);
        } finally { recovered.close(); jdbc.update("DELETE FROM election_result_snapshots"); }
    }
}
