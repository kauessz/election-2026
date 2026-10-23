package br.com.election;

import br.com.election.aggregate.*;
import br.com.election.poll.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AggregateApiTests.TimeConfiguration.class})
class AggregateApiTests {
    static final LocalDate REFERENCE = LocalDate.of(2026, 10, 22);
    static final UUID C1 = new UUID(0, 1), C2 = new UUID(0, 2);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PollService polls;
    @Autowired AggregateService aggregates;
    @Autowired AggregateSnapshotService snapshots;
    @Autowired EntityManagerFactory emf;
    @Autowired AdjustableClock clock;
    List<UUID> pollsters;

    @TestConfiguration(proxyBeanMethods=false)
    static class TimeConfiguration {
        @Bean @Primary AdjustableClock testClock() { return new AdjustableClock(); }
    }
    static class AdjustableClock extends Clock {
        private Instant now = Instant.parse("2026-10-22T15:00:30Z");
        void reset() { now = Instant.parse("2026-10-22T15:00:30Z"); }
        void advanceMinute() { now = now.plusSeconds(60); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM aggregate_snapshots");
        jdbc.update("DELETE FROM polls");
        jdbc.update("DELETE FROM pollster_performance");
        jdbc.update("UPDATE pollsters SET active=true");
        jdbc.update("UPDATE candidates SET active=true");
        pollsters = jdbc.queryForList("SELECT id FROM pollsters ORDER BY slug", UUID.class);
        clock.reset();
    }
    PollResponse poll(UUID pollster, String publication, int fieldAge, String first, String margin) {
        LocalDate field = REFERENCE.minusDays(fieldAge);
        return polls.create(new PollRequest(pollster, null, field, field, Instant.parse(publication), 1000,
            margin == null ? null : new BigDecimal(margin), null, "https://example.org/test", VoteBasis.VALID,
            List.of(new PollRequest.Result(C1, new BigDecimal(first)), new PollRequest.Result(C2, new BigDecimal("100").subtract(new BigDecimal(first))))));
    }
    PollResponse poll(UUID pollster, String first) { return poll(pollster, "2026-10-22T12:00:00Z", 0, first, "2"); }
    String performance(UUID institute, String error) {
        return """
            {"pollsterId":"%s","electionKey":"BR-PRESIDENT-2026","round":1,
             "candidateError":1,"spreadError":%s,"sourceUrl":"https://example.org/documented-test-fixture"}
            """.formatted(institute, error);
    }
    @Test void latestOnlyIgnoresExtraRounds() {
        var institute = pollsters.getFirst();
        poll(institute, "2026-10-20T12:00:00Z", 2, "90", "2");
        poll(institute, "2026-10-21T12:00:00Z", 1, "80", "2");
        var newest = poll(institute, "60"); poll(pollsters.get(1), "40");
        var result = aggregates.all(REFERENCE);
        assertThat(result).allSatisfy(a -> {
            assertThat(a.pollCount()).isEqualTo(2);
            assertThat(a.contributions().stream().filter(c -> c.pollsterId().equals(institute)).map(AggregateResponse.Contribution::pollId)).containsExactly(newest.id());
        });
        assertThat(result.getFirst().results().getFirst().percentage()).isEqualByComparingTo("50");
    }
    @Test void inactiveInstituteExcludedForEveryModel() {
        poll(pollsters.getFirst(), "90"); poll(pollsters.get(1), "40");
        jdbc.update("UPDATE pollsters SET active=false WHERE id=?", pollsters.getFirst());
        assertThat(aggregates.all(REFERENCE)).allSatisfy(a -> {
            assertThat(a.pollsterCount()).isEqualTo(1);
            assertThat(a.results().getFirst().percentage()).isEqualByComparingTo("40");
            assertThat(a.excludedPolls().getFirst().reason()).contains("inativo");
        });
    }
    @Test void referenceDateExcludesFuturePublicationsWithoutLosingOlderRound() {
        var previous = poll(pollsters.getFirst(), "2026-10-21T12:00:00Z", 2, "40", "2");
        poll(pollsters.getFirst(), "2026-10-23T12:00:00Z", 0, "80", "2");
        assertThat(aggregates.one(AggregateMethod.SIMPLE, REFERENCE).contributions().getFirst().pollId()).isEqualTo(previous.id());
    }
    @Test void cutoffUsesBrazilianCalendarDate() {
        var evening = poll(pollsters.getFirst(), "2026-10-23T02:30:00Z", 0, "55", "2");
        poll(pollsters.getFirst(), "2026-10-23T03:00:00Z", 0, "90", "2");
        assertThat(aggregates.one(AggregateMethod.SIMPLE, REFERENCE).contributions().getFirst().pollId()).isEqualTo(evening.id());
    }
    @Test void incompleteLatestIsExcludedWithoutFallingBackToOlderPoll() {
        poll(pollsters.getFirst(), "2026-10-21T12:00:00Z", 1, "60", "2");
        polls.create(new PollRequest(pollsters.getFirst(), null, REFERENCE, REFERENCE, Instant.parse("2026-10-22T12:00:00Z"), 1000,
            BigDecimal.ONE, null, "https://example.org/test", VoteBasis.VALID, List.of(new PollRequest.Result(C1, new BigDecimal("50")))));
        var result = aggregates.one(AggregateMethod.SIMPLE, REFERENCE);
        assertThat(result.pollCount()).isZero(); assertThat(result.excludedPolls().getFirst().reason()).contains("completo");
    }
    @Test void futureFieldIsExcluded() {
        polls.create(new PollRequest(pollsters.getFirst(), null, REFERENCE, REFERENCE.plusDays(1), Instant.parse("2026-10-22T12:00:00Z"), 1000,
            BigDecimal.ONE, null, "https://example.org/test", VoteBasis.VALID,
            List.of(new PollRequest.Result(C1, new BigDecimal("50")), new PollRequest.Result(C2, new BigDecimal("50")))));
        assertThat(aggregates.one(AggregateMethod.SIMPLE, REFERENCE).excludedPolls().getFirst().reason()).contains("Campo");
    }
    @Test void noDataReturnsFourEmptyModelsAndNoSnapshots() throws Exception {
        mvc.perform(get("/api/aggregates")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)))
            .andExpect(jsonPath("$[0].results", hasSize(0))).andExpect(jsonPath("$[0].spread").doesNotExist());
        mvc.perform(post("/api/admin/aggregates/snapshot")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aggregate_snapshots", Integer.class)).isZero();
    }
    @Test void allFourIndividualEndpointsAndCombinedAgree() throws Exception {
        poll(pollsters.getFirst(), "60");
        for (String method : new String[]{"simple", "recency", "weighted", "calibrated"}) {
            mvc.perform(get("/api/aggregates/" + method).param("referenceDate", REFERENCE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.method").value(method.toUpperCase()))
                .andExpect(jsonPath("$.results[0].percentage").value(60));
        }
        mvc.perform(get("/api/aggregates").param("referenceDate", REFERENCE.toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4))).andExpect(jsonPath("$[3].method").value("CALIBRATED"));
    }
    @Test void getsNeverPersistSnapshots() throws Exception {
        poll(pollsters.getFirst(), "60");
        mvc.perform(get("/api/aggregates")).andExpect(status().isOk());
        mvc.perform(get("/api/aggregates/simple")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aggregate_snapshots", Integer.class)).isZero();
    }
    @Test void createsSnapshotsAndStoresCompleteCompositionAsJsonb() throws Exception {
        poll(pollsters.getFirst(), "60");
        mvc.perform(post("/api/admin/aggregates/snapshot")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aggregate_snapshots WHERE payload->'contributions'->0->>'pollsterName' IS NOT NULL AND payload->'configuration' IS NOT NULL", Integer.class)).isEqualTo(4);
        mvc.perform(get("/api/aggregates/history").param("method", "SIMPLE")).andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].results[0].percentage").value(60));
    }
    @Test void duplicateWithinMinuteReturnsOriginalSnapshots() {
        poll(pollsters.getFirst(), "60");
        var first = snapshots.create(REFERENCE);
        poll(pollsters.getFirst(), "2026-10-22T14:00:00Z", 0, "80", "2");
        var second = snapshots.create(REFERENCE);
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aggregate_snapshots", Integer.class)).isEqualTo(4);
    }
    @Test void subsequentMinuteCreatesHistoryInChronologicalOrder() {
        poll(pollsters.getFirst(), "60"); snapshots.create(REFERENCE);
        clock.advanceMinute(); snapshots.create(REFERENCE);
        var history = snapshots.history(AggregateMethod.CALIBRATED);
        assertThat(history).hasSize(2);
        assertThat(history.getFirst().calculatedAt()).isBefore(history.getLast().calculatedAt());
        assertThat(snapshots.history(null)).hasSize(8);
    }
    @Test void sameMinuteDifferentReferenceDateHasSeparateSnapshot() {
        poll(pollsters.getFirst(), "2026-10-21T12:00:00Z", 1, "60", "2");
        snapshots.create(REFERENCE); snapshots.create(REFERENCE.minusDays(1));
        assertThat(snapshots.history(AggregateMethod.SIMPLE)).hasSize(2);
    }
    @Test void historySurvivesDeletionOfSourcePoll() {
        var poll = poll(pollsters.getFirst(), "60"); snapshots.create(REFERENCE); polls.delete(poll.id());
        assertThat(snapshots.history(AggregateMethod.SIMPLE).getFirst().results().getFirst().percentage()).isEqualByComparingTo("60");
    }
    @Test void performanceCanBeCreatedEditedAndScoreIsDerived() throws Exception {
        mvc.perform(post("/api/admin/pollster-performances").contentType("application/json").content(performance(pollsters.getFirst(), "0")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.historicalScore").value(1.15));
        UUID id = jdbc.queryForObject("SELECT id FROM pollster_performance", UUID.class);
        mvc.perform(put("/api/admin/pollster-performances/" + id).contentType("application/json").content(performance(pollsters.getFirst(), "6")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.historicalScore").value(0.85));
        mvc.perform(get("/api/admin/pollster-performances")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }
    @Test void performanceRejectsDuplicatesInvalidErrorsAndMissingReferences() throws Exception {
        String valid = performance(pollsters.getFirst(), "3");
        mvc.perform(post("/api/admin/pollster-performances").contentType("application/json").content(valid)).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/pollster-performances").contentType("application/json").content(valid)).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/pollster-performances").contentType("application/json").content(performance(pollsters.get(1), "-1"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/pollster-performances").contentType("application/json").content(performance(UUID.randomUUID(), "1"))).andExpect(status().isNotFound());
    }
    @Test void calibratedUsesOnlyConfiguredElectionAndRound() throws Exception {
        for (int i = 0; i < 8; i++) poll(pollsters.get(i), i == 0 ? "60" : "40");
        mvc.perform(post("/api/admin/pollster-performances").contentType("application/json").content(performance(pollsters.getFirst(), "0"))).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/pollster-performances").contentType("application/json").content(performance(pollsters.get(1), "0").replace("BR-PRESIDENT-2026", "OTHER-ELECTION"))).andExpect(status().isCreated());
        var calibrated = aggregates.one(AggregateMethod.CALIBRATED, REFERENCE);
        assertThat(calibrated.contributions().stream().filter(c -> c.pollsterId().equals(pollsters.getFirst())).findFirst().orElseThrow().historicalWeightFactor()).isEqualByComparingTo("1.15");
        assertThat(calibrated.contributions().stream().filter(c -> c.pollsterId().equals(pollsters.get(1))).findFirst().orElseThrow().historicalWeightFactor()).isEqualByComparingTo("1");
        assertThat(calibrated.contributions()).allSatisfy(c -> assertThat(c.normalizedWeight()).isLessThanOrEqualTo(new BigDecimal("0.15")));
    }
    @Test void invalidReferenceAndMethodReturnProblemDetails() throws Exception {
        mvc.perform(get("/api/aggregates").param("referenceDate", "not-a-date")).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/aggregates/history").param("method", "OTHER")).andExpect(status().isBadRequest());
    }
    @Test void combinedEndpointHasBoundedQueriesAndLoadsLatestOnlyOnce() {
        for (int i = 0; i < 14; i++) poll(pollsters.get(i), "50");
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true); statistics.clear();
        try {
            assertThat(aggregates.all(REFERENCE)).hasSize(4);
            // latest, fetched pollsters, batched results/candidates, active candidates, history.
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
        } finally { statistics.setStatisticsEnabled(false); }
    }
}
