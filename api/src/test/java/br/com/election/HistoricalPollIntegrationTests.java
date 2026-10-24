package br.com.election;

import br.com.election.aggregate.*;
import br.com.election.poll.PollService;
import br.com.election.pollhistory.*;
import br.com.election.readiness.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class,HistoricalPollIntegrationTests.TimeConfiguration.class})
class HistoricalPollIntegrationTests {
    static final String ELECTION="BR-PRESIDENT-2026";
    static final LocalDate REFERENCE=LocalDate.of(2026,10,23);
    @TestConfiguration(proxyBeanMethods=false) static class TimeConfiguration {
        @Bean @Primary Clock historyTestClock() { return Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"),ZoneOffset.UTC); }
    }
    @Autowired HistoricalPollImportService imports;
    @Autowired HistoricalPollStore store;
    @Autowired BatchImportService currentImports;
    @Autowired AggregateService aggregates;
    @Autowired AggregateSnapshotService snapshots;
    @Autowired FreezeService freezes;
    @Autowired PollService polls;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired MockMvc mvc;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM historical_polls"); jdbc.execute("TRUNCATE pre_election_freezes");
        jdbc.update("DELETE FROM aggregate_snapshots"); jdbc.update("DELETE FROM polls"); jdbc.update("DELETE FROM pollster_performance");
        jdbc.update("DELETE FROM candidates WHERE ballot_number=55");
        jdbc.update("UPDATE candidates SET active=true"); jdbc.update("UPDATE pollsters SET active=true");
    }
    String row(int round,String registration) {
        return """
            {"electionKey":"BR-PRESIDENT-2026","round":%d,"pollsterSlug":"datafolha",
             "registrationNumber":%s,"fieldStart":"2026-09-28","fieldEnd":"2026-09-30","publishedAt":"2026-10-01T12:00:00Z",
             "sampleSize":1500,"marginOfError":2,"sourceUrl":"https://example.org/SYNTHETIC-HISTORY-TEST",
             "methodology":"SYNTHETIC TEST ONLY","voteBasis":"TOTAL","normalization":"NONE",
             "candidateResults":[{"candidateKey":"synthetic-a","candidateName":"Candidato sintético A","ballotNumber":22,"originalPercentage":40},
             {"candidateKey":"synthetic-b","candidateName":"Candidato sintético B","ballotNumber":13,"originalPercentage":35},
             {"candidateKey":"synthetic-c","candidateName":"Candidato sintético C","ballotNumber":55,"originalPercentage":5}]}
            """.formatted(round,registration==null ? "null" : "\""+registration+"\"");
    }
    String complete(String row) { return row.replace("\"normalization\":\"NONE\"", "\"normalization\":\"VALID_VOTES\",\"completeCandidateSet\":true,\"expectedCandidateKeys\":[\"synthetic-c\",\"synthetic-a\",\"synthetic-b\"]"); }
    ImportModels.Report run(String rows,boolean dry) { return imports.run(json.readTree("["+rows+"]"),dry); }

    @Test void dryRunPreviewsCompleteSetNormalizationWithoutWrites() {
        var report=run(complete(row(1,"TEST")),true);
        assertThat(report.created()).isEqualTo(1); assertThat(report.failed()).isZero();
        assertThat(json.writeValueAsString(report.details().getFirst().normalized())).contains("50.0000","43.7500","6.2500");
        assertThat(store.list(ELECTION,null)).isEmpty(); assertThat(jdbc.queryForObject("SELECT count(*) FROM historical_poll_results",Integer.class)).isZero();
    }
    @Test void partialFirstRoundIsStoredWithoutInventedNormalization() {
        String pair=row(1,"PAIR");
        var raw=json.readTree(pair); var node=json.valueToTree(json.treeToValue(raw,HistoricalPollModels.Request.class));
        // Build a true two-candidate cut using the JSON tree, not the active candidates table.
        var object=(tools.jackson.databind.node.ObjectNode) node;
        var array=json.createArrayNode(); array.add(node.get("candidateResults").get(0)); array.add(node.get("candidateResults").get(1)); object.set("candidateResults",array);
        var report=imports.run(json.createArrayNode().add(object),false);
        assertThat(report.created()).isEqualTo(1);
        assertThat(store.list(ELECTION,1).getFirst().results()).hasSize(2).allSatisfy(r -> assertThat(r.normalizedPercentage()).isNull());
    }
    @Test void realImportIdempotenceUpdateAndTimestamps() {
        assertThat(run(row(1,"TEST"),false).created()).isEqualTo(1);
        var before=store.list(ELECTION,1).getFirst();
        assertThat(run(row(1,"TEST").replace("\"marginOfError\":2","\"marginOfError\":2.0000"),false).unchanged()).isEqualTo(1);
        assertThat(store.list(ELECTION,1).getFirst()).isEqualTo(before);
        assertThat(run(row(1,"TEST").replace("\"sampleSize\":1500","\"sampleSize\":2000"),true).updated()).isEqualTo(1);
        assertThat(store.list(ELECTION,1).getFirst()).isEqualTo(before);
        assertThat(run(row(1,"TEST").replace("\"sampleSize\":1500","\"sampleSize\":2000"),false).updated()).isEqualTo(1);
        var after=store.list(ELECTION,1).getFirst(); assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.createdAt()).isEqualTo(before.createdAt()); assertThat(after.sampleSize()).isEqualTo(2000);
    }
    @Test void fallbackKeyAndResultsOrderAreStable() {
        var original=row(1,null);
        assertThat(run(original,false).created()).isEqualTo(1);
        var request=json.treeToValue(json.readTree(original),HistoricalPollModels.Request.class);
        var reversed=new ArrayList<>(request.candidateResults()); Collections.reverse(reversed);
        var changed=new HistoricalPollModels.Request(request.electionKey(),request.round(),request.pollsterSlug(),request.registrationNumber(),request.fieldStart(),request.fieldEnd(),request.publishedAt(),request.sampleSize(),request.marginOfError(),request.methodology(),request.sourceUrl(),request.voteBasis(),request.normalization(),request.completeCandidateSet(),request.expectedCandidateKeys(),request.notes(),reversed);
        assertThat(run(json.writeValueAsString(changed),false).unchanged()).isEqualTo(1);
    }
    @Test void concurrentImportsPreserveNaturalKeyUniqueness() throws Exception {
        var first=java.util.concurrent.CompletableFuture.supplyAsync(() -> run(row(1,"CONCURRENT"),false));
        var second=java.util.concurrent.CompletableFuture.supplyAsync(() -> run(row(1,"CONCURRENT"),false));
        var a=first.get(30,java.util.concurrent.TimeUnit.SECONDS); var b=second.get(30,java.util.concurrent.TimeUnit.SECONDS);
        assertThat(a.failed()+b.failed()).isZero(); assertThat(a.created()+b.created()).isEqualTo(1);
        assertThat(a.unchanged()+b.unchanged()).isEqualTo(1); assertThat(store.list(ELECTION,1)).hasSize(1);
    }
    @Test void dryRunSimulatesDuplicateRowsAndFailedRowsAreIsolated() {
        var preview=run(row(1,"SAME")+","+row(1,"SAME"),true);
        assertThat(preview.created()).isEqualTo(1); assertThat(preview.unchanged()).isEqualTo(1);
        assertThat(store.list(ELECTION,null)).isEmpty();
        var report=run(row(1,"A")+","+row(1,"BAD").replace("datafolha","missing-pollster")+","+row(2,"B"),false);
        assertThat(report.created()).isEqualTo(2); assertThat(report.failed()).isEqualTo(1);
        assertThat(store.list(ELECTION,null)).hasSize(2);
    }
    @Test void readEndpointsSeparateRoundsAndElectionKeys() throws Exception {
        run(row(1,"SAME")+","+row(2,"SAME")+","+row(1,"OTHER").replace(ELECTION,"OTHER-ELECTION"),false);
        mvc.perform(get("/api/poll-history")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        for (int round:List.of(1,2)) mvc.perform(get("/api/poll-history").param("round",""+round))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].round").value(round));
        mvc.perform(get("/api/poll-history").param("electionKey","OTHER-ELECTION")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/poll-history").param("round","3")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/poll-history").param("electionKey","invalid key")).andExpect(status().isBadRequest());
    }
    @Test void importEndpointDefaultsToDryRunAndAcceptsEmptyPreparation() throws Exception {
        mvc.perform(post("/api/admin/poll-history/import").contentType("application/json").content("{\"polls\":["+row(1,"ENDPOINT")+"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true)).andExpect(jsonPath("$.created").value(1));
        assertThat(store.list(ELECTION,1)).isEmpty();
        mvc.perform(post("/api/admin/poll-history/import?dryRun=false").contentType("application/json").content("["+row(1,"ENDPOINT")+"]"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));
        mvc.perform(post("/api/admin/poll-history/import?dryRun=true").contentType("application/json").content("["+row(1,"ENDPOINT")+"]"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unchanged").value(1));
        mvc.perform(post("/api/admin/poll-history/import").contentType("application/json").content("{\"polls\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(0));
        mvc.perform(post("/api/admin/poll-history/import").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }
    @Test void normalizationRequiresDeclaredAndCompleteCandidateSet() {
        assertThat(run(row(1,"NO-DECLARATION").replace("\"NONE\"","\"VALID_VOTES\""),true).failed()).isEqualTo(1);
        assertThat(run(complete(row(1,"MISSING")).replace("\"synthetic-c\",\"synthetic-a\",\"synthetic-b\"","\"synthetic-a\",\"synthetic-b\""),true).failed()).isEqualTo(1);
        var request=json.treeToValue(json.readTree(complete(row(1,"PAIR"))),HistoricalPollModels.Request.class);
        var pair=new HistoricalPollModels.Request(request.electionKey(),1,request.pollsterSlug(),request.registrationNumber(),request.fieldStart(),request.fieldEnd(),request.publishedAt(),request.sampleSize(),request.marginOfError(),request.methodology(),request.sourceUrl(),request.voteBasis(),request.normalization(),true,List.of("synthetic-a","synthetic-b"),null,request.candidateResults().subList(0,2));
        assertThat(run(json.writeValueAsString(pair),true).details().getFirst().message()).contains("par do primeiro turno");
    }
    @Test void declaredValidPercentagesAreNotRescaledAndMustSumApproximately100() {
        var valid=complete(row(1,"VALID")).replace("\"TOTAL\"","\"VALID\"").replace("\"originalPercentage\":5","\"originalPercentage\":25");
        assertThat(run(valid,false).created()).isEqualTo(1);
        assertThat(store.list(ELECTION,1).getFirst().results()).allSatisfy(r -> assertThat(r.normalizedPercentage()).isEqualByComparingTo(r.originalPercentage()));
        assertThat(run(complete(row(1,"INVALID")).replace("\"TOTAL\"","\"VALID\""),true).failed()).isEqualTo(1);
    }
    @Test void validationRejectsBadTypesDatesSourcesSamplesAndPercentages() {
        var base=row(1,"INVALID");
        for (String invalid:List.of(base.replace("\"round\":1","\"round\":3"),
            base.replace("2026-09-28","2026-10-02"),base.replace("2026-10-01T12:00:00Z","2026-09-29T12:00:00Z"),
            base.replace("https://example.org/SYNTHETIC-HISTORY-TEST","javascript:invalid"),base.replace("\"sampleSize\":1500","\"sampleSize\":0"),
            base.replace("\"originalPercentage\":40","\"originalPercentage\":40.00001"),
            base.replace("\"originalPercentage\":40","\"originalPercentage\":101"),
            base.replace("\"originalPercentage\":40","\"normalizedPercentage\":50,\"originalPercentage\":40"),
            base.replace("\"synthetic-c\"","\"synthetic-a\""),base.replace("\"ballotNumber\":55","\"ballotNumber\":22")))
            assertThat(run(invalid,true).failed()).as(invalid).isEqualTo(1);
    }
    @Test void unknownOrInactiveHistoricalCandidatesNeverTouchCandidatesTable() {
        jdbc.update("INSERT INTO candidates (name,display_name,party,ballot_number,active) VALUES ('TEST inactive','TEST inactive','TEST',55,false)");
        UUID first=jdbc.queryForObject("SELECT id FROM candidates WHERE ballot_number=22",UUID.class);
        UUID second=jdbc.queryForObject("SELECT id FROM candidates WHERE ballot_number=13",UUID.class);
        UUID institute=jdbc.queryForObject("SELECT id FROM pollsters WHERE slug='datafolha'",UUID.class);
        polls.create(new br.com.election.poll.PollRequest(institute,"CURRENT-TEST",REFERENCE.minusDays(3),REFERENCE.minusDays(2),
            Instant.parse("2026-10-22T12:00:00Z"),1000,new BigDecimal("2"),"SYNTHETIC TEST", "https://example.org/SYNTHETIC-TEST",br.com.election.poll.VoteBasis.VALID,
            List.of(new br.com.election.poll.PollRequest.Result(first,new BigDecimal("51")),new br.com.election.poll.PollRequest.Result(second,new BigDecimal("49")))));
        var aggregatesBefore=aggregates.all(REFERENCE);
        var before=jdbc.queryForList("SELECT * FROM candidates ORDER BY id");
        assertThat(run(row(1,"INACTIVE"),false).created()).isEqualTo(1);
        assertThat(run(row(2,"UNKNOWN").replace("\"ballotNumber\":55","\"ballotNumber\":66"),false).created()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM candidates ORDER BY id")).isEqualTo(before);
        assertThat(polls.list()).hasSize(1); assertThat(aggregates.all(REFERENCE)).isEqualTo(aggregatesBefore);
    }
    @Test void operationalDatasetKeepsExactFourModelsSnapshotsFreezeAndCurrentPolls() throws Exception {
        var root=Path.of("..").toAbsolutePath().normalize();
        for (String slug:List.of("datatrends","vox-brasil","instituto-franca","meio-ideia","alfa-inteligencia"))
            jdbc.update("INSERT INTO pollsters (name,slug) VALUES (?,?) ON CONFLICT (slug) DO NOTHING",slug,slug);
        var pollReport=currentImports.run(json.readTree(Files.readString(root.resolve("data/polls.real.2026-10-23.json"))),false,false);
        var performanceReport=currentImports.run(json.readTree(Files.readString(root.resolve("data/pollster-performance.real.2026-first-round.json"))),false,true);
        assertThat(pollReport.created()).isEqualTo(18); assertThat(pollReport.failed()).isZero();
        assertThat(performanceReport.created()).isEqualTo(17); assertThat(performanceReport.failed()).isZero();
        var before=aggregates.all(REFERENCE); var originalPolls=polls.list();
        String[][] expected={{"50.9551","49.0449"},{"50.9383","49.0617"},{"50.9630","49.0370"},{"50.9549","49.0451"}};
        UUID flavio=jdbc.queryForObject("SELECT id FROM candidates WHERE ballot_number=22",UUID.class);
        for (int i=0;i<before.size();i++) {
            var a=before.get(i); assertThat(a.pollCount()).isEqualTo(18); assertThat(a.totalSampleSize()).isEqualTo(59442);
            for (var r:a.results()) assertThat(r.percentage()).isEqualByComparingTo(expected[i][r.candidateId().equals(flavio)?0:1]);
        }
        snapshots.create(REFERENCE); var savedSnapshots=jdbc.queryForList("SELECT * FROM aggregate_snapshots ORDER BY id");
        var frozen=freezes.create(new FreezeService.Request("TEST archive isolation",null,"Disposable PostgreSQL only"));
        var frozenBefore=freezes.active();
        assertThat(run(complete(row(1,"HISTORY"))+","+row(2,"HISTORY"),false).created()).isEqualTo(2);
        assertThat(aggregates.all(REFERENCE)).isEqualTo(before);
        assertThat(polls.list()).isEqualTo(originalPolls);
        assertThat(jdbc.queryForList("SELECT * FROM aggregate_snapshots ORDER BY id")).isEqualTo(savedSnapshots);
        assertThat(freezes.active()).isEqualTo(frozenBefore); assertThat(frozen.id()).isEqualTo(frozenBefore.id());
        mvc.perform(get("/api/polls")).andExpect(jsonPath("$.length()").value(18));
        mvc.perform(get("/api/polls/latest")).andExpect(jsonPath("$.length()").value(18));
        mvc.perform(get("/api/poll-history")).andExpect(jsonPath("$.length()").value(2));
    }
}
