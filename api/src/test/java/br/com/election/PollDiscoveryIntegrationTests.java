package br.com.election;

import br.com.election.discovery.*;
import br.com.election.discovery.DiscoveryModels.*;
import br.com.election.aggregate.AggregateSnapshotService;
import br.com.election.poll.VoteBasis;
import br.com.election.readiness.BatchImportService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.admin.api-key=discovery-test-key") @AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class,PollDiscoveryIntegrationTests.TimeConfiguration.class})
class PollDiscoveryIntegrationTests {
    @TestConfiguration(proxyBeanMethods=false) static class TimeConfiguration {@Bean @Primary Clock discoveryClock(){return Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"),ZoneOffset.UTC);}}
    @Autowired PollDiscoveryService service;@Autowired PollDiscoveryStore store;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired JsonMapper json;
    @MockitoBean TsePollRegistryClient client;
    @MockitoSpyBean BatchImportService importer;@MockitoSpyBean AggregateSnapshotService snapshots;
    @BeforeEach void clean(){jdbc.update("DELETE FROM poll_discovery_events");jdbc.update("DELETE FROM poll_discoveries");jdbc.update("DELETE FROM aggregate_snapshots");
        jdbc.update("DELETE FROM polls");jdbc.update("DELETE FROM pollster_performance");jdbc.update("UPDATE candidates SET active=true");jdbc.update("UPDATE pollsters SET active=true");}
    TsePollRegistryParser.Entry entry(String reg,String name){return new TsePollRegistryParser.Entry(reg,name,name,LocalDate.parse("2026-10-19"),LocalDate.parse("2026-10-21"),LocalDate.parse("2026-10-22"),1500,"SYNTHETIC TEST ONLY",Map.of("registration",reg,"name",name));}
    Discovery detect(){service.stage(List.of(entry("BR-01234/2026","Datafolha")));return service.list(null,null,null).items().getFirst();}
    Review review(String notes){return new Review("datafolha",LocalDate.parse("2026-10-19"),LocalDate.parse("2026-10-21"),Instant.parse("2026-10-23T12:00:00Z"),1500,new BigDecimal("2"),"SYNTHETIC TEST ONLY","https://example.org/synthetic-publication",VoteBasis.VALID,List.of(new Result(22,new BigDecimal("51")),new Result(13,new BigDecimal("49"))),notes,true,true);}
    Discovery ready(){var d=detect();return service.review(d.id,review("Conferido pelo operador; teste sintético"));}
    Discovery preview(){var d=ready();assertThat(service.dryRun(d.id).failed()).isZero();return service.get(d.id);}
    int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    @Test void syncStagesMapsAndIsIdempotentWithoutOperationalWrites(){var d=detect();assertThat(d.status).isEqualTo(Status.AWAITING_RESULTS);assertThat(d.pollsterSlug).isEqualTo("datafolha");
        var second=service.stage(List.of(entry("BR-01234/2026","Datafolha")));assertThat(second.unchanged()).isEqualTo(1);assertThat(count("poll_discoveries")).isEqualTo(1);
        assertThat(count("polls")).isZero();assertThat(count("aggregate_snapshots")).isZero();assertThat(service.get(d.id).detectedAt).isEqualTo(d.detectedAt);}
    @Test void unknownInstituteIsNeverCreatedAndCanBeAssociatedByOperator(){service.stage(List.of(entry("BR-09999/2026","Unknown Institute")));var d=service.list(null,null,null).items().getFirst();
        assertThat(d.status).isEqualTo(Status.UNMAPPED_POLLSTER);assertThat(d.pollsterSlug).isNull();int institutes=count("pollsters");service.review(d.id,review(null));assertThat(count("pollsters")).isEqualTo(institutes);}
    @Test void verifiedLegalAliasMapsWithoutFuzzyMatchingOrInstituteCreation(){
        int before=count("pollsters");service.stage(List.of(entry("BR-01234/2026","DATAFOLHA INSTITUTO DE PESQUISAS LTDA."),entry("BR-09999/2026","DATAFOLHA INSTITUTO DE PESQUISAS NOVO LTDA.")));
        var mapped=service.list(null,null,"datafolha").items().stream().filter(d->d.registrationNumber.equals("BR-01234/2026")).findFirst().orElseThrow();
        assertThat(mapped.pollsterSlug).isEqualTo("datafolha");assertThat(mapped.status).isEqualTo(Status.AWAITING_RESULTS);
        assertThat(mapped.secondRoundConfirmed).isFalse();assertThat(mapped.publicationAllowedConfirmed).isFalse();
        assertThat(service.list(Status.UNMAPPED_POLLSTER,null,null).items()).hasSize(1);assertThat(count("pollsters")).isEqualTo(before);
    }
    @Test void stagingCutoffDoesNotCopyFirstRound(){var e=entry("BR-01234/2026","Datafolha");
        service.stage(List.of(new TsePollRegistryParser.Entry(e.registrationNumber(),e.pollsterName(),e.legalName(),e.fieldStart(),e.fieldEnd(),LocalDate.parse("2026-10-04"),e.sampleSize(),e.methodology(),e.metadata())));
        assertThat(count("poll_discoveries")).isZero();assertThat(count("polls")).isZero();
    }
    @Test void newAliasesRemapExistingUnmappedDiscoveriesWithUnchangedRegistryHash(){
        var entries=List.of(entry("BR-00001/2026","MDA-PESQUISA DE OPINIAO PUBLICA E CONSULT. ESTATIST. LTDA - EPP"),
            entry("BR-00002/2026","QUAEST PESQUISAS, CONSULTORIA E PROJETOS LTDA."),
            entry("BR-00003/2026","SIMPLEX CONSULTORIA ECONOMICA E EMPRESARIAL"),
            entry("BR-00004/2026","INSTITUTO FRANCA DE PESQUISA PESQUISA E ASSESSORIA"),
            entry("BR-00005/2026","COLECTTA INSTITUTO DE PESQUISA E ESTATISTICA LTDA"),entry("BR-00006/2026","100 CIDADES"));
        int pollsters=count("pollsters");service.stage(entries);
        var expected=Map.of("BR-00001/2026","cnt-mda","BR-00002/2026","quaest");
        var legacy=new HashMap<UUID,String>();
        for(var d:service.list(null,null,null).items())if(expected.containsKey(d.registrationNumber)){
            // Persist the state left by a sync before these aliases existed; keep the original registry hash.
            legacy.put(d.id,d.registryHash);d.pollsterSlug=null;d.status=Status.UNMAPPED_POLLSTER;store.save(d,"TEST_PRE_ALIAS_STATE");
        }
        int events=count("poll_discovery_events");var sync=service.stage(entries);
        assertThat(sync.created()).isZero();assertThat(sync.updated()).isEqualTo(2);assertThat(sync.unchanged()).isEqualTo(4);
        for(var d:service.list(null,null,null).items()){
            if(expected.containsKey(d.registrationNumber)){
                assertThat(d.pollsterSlug).isEqualTo(expected.get(d.registrationNumber));assertThat(d.status).isEqualTo(Status.AWAITING_RESULTS);
                assertThat(d.registryHash).isEqualTo(legacy.get(d.id));assertThat(d.secondRoundConfirmed).isFalse();assertThat(d.publicationAllowedConfirmed).isFalse();
            }else {assertThat(d.pollsterSlug).isNull();assertThat(d.status).isEqualTo(Status.UNMAPPED_POLLSTER);}
        }
        assertThat(count("poll_discovery_events")).isEqualTo(events+2);
        var again=service.stage(entries);assertThat(again.created()).isZero();assertThat(again.updated()).isZero();assertThat(again.unchanged()).isEqualTo(6);
        assertThat(count("pollsters")).isEqualTo(pollsters);assertThat(count("polls")).isZero();assertThat(count("aggregate_snapshots")).isZero();
    }
    @Test void terminalDiscoveriesAlsoReceiveNewAliasOnceWithoutReopening(){
        var entries=List.of(entry("BR-00001/2026","MDA-PESQUISA DE OPINIAO PUBLICA E CONSULT. ESTATIST. LTDA - EPP"),
            entry("BR-00002/2026","QUAEST PESQUISAS, CONSULTORIA E PROJETOS LTDA."));
        service.stage(entries);var hashes=new HashMap<UUID,String>();var statuses=new HashMap<UUID,Status>();
        for(var d:service.list(null,null,null).items()){
            d.pollsterSlug=null;d.status=d.registrationNumber.equals("BR-00001/2026")?Status.IMPORTED:Status.IGNORED;
            // Logical imported reference is retained even if its operational poll was removed externally.
            if(d.status==Status.IMPORTED)d.importedPollId=UUID.randomUUID();
            hashes.put(d.id,d.registryHash);statuses.put(d.id,d.status);store.save(d,"TEST_PRE_ALIAS_TERMINAL_STATE");
        }
        assertThat(service.stage(entries).updated()).isEqualTo(2);
        for(var d:service.list(null,null,null).items()){
            assertThat(d.pollsterSlug).isEqualTo(d.registrationNumber.equals("BR-00001/2026")?"cnt-mda":"quaest");
            assertThat(d.registryHash).isEqualTo(hashes.get(d.id));assertThat(d.status).isEqualTo(statuses.get(d.id));
        }
        var again=service.stage(entries);assertThat(again.updated()).isZero();assertThat(again.unchanged()).isEqualTo(2);
        assertThat(count("polls")).isZero();assertThat(count("aggregate_snapshots")).isZero();
    }
    @Test void expectedTodayAndDetectedTodayAreIndependentBrasiliaMetricsWithOperationalOrdering() throws Exception {
        var e=entry("BR-01234/2026","Datafolha");
        for(int day:List.of(22,24,25))service.stage(List.of(new TsePollRegistryParser.Entry("BR-000"+day+"/2026",e.pollsterName(),e.legalName(),e.fieldStart(),e.fieldEnd(),LocalDate.of(2026,10,day),e.sampleSize(),e.methodology(),Map.of("day",""+day))));
        var today=service.list(null,null,null).items().getFirst();today.detectedAt=Instant.parse("2026-10-24T01:00:00Z");store.save(today,"TEST_TIMEZONE_ONLY");
        var inbox=service.list(null,null,null);assertThat(inbox.detectedToday()).isEqualTo(2);assertThat(inbox.expectedPublicationToday()).isEqualTo(1);
        assertThat(inbox.items()).extracting(d->d.expectedPublicationDate.getDayOfMonth()).containsExactly(24,25,22);
        mvc.perform(get("/api/admin/poll-discoveries").param("expectedPublicationDate","2026-10-24").header("X-Admin-Key","discovery-test-key"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.expectedPublicationToday").value(1));
        mvc.perform(get("/api/admin/poll-discoveries").param("expectedPublicationDate","2026-10-24").param("date","2026-10-23").header("X-Admin-Key","discovery-test-key")).andExpect(status().isBadRequest());
    }
    @Test void observedFixtureSyncPreservesEighteenOperationalPollsAndSecondSyncCreatesZero() throws Exception {
        var r=review(null);var rows=new ArrayList<br.com.election.readiness.ImportModels.PollRow>();
        for(int i=0;i<18;i++)rows.add(new br.com.election.readiness.ImportModels.PollRow("datafolha",i==0?"BR-09709/2026":String.format("BR-%05d/2026",5000+i),r.fieldStart(),r.fieldEnd(),r.publishedAt(),r.sampleSize(),r.marginOfError(),r.methodology(),r.sourceUrl(),r.voteBasis(),r.candidateResults().stream().map(v->new br.com.election.readiness.ImportModels.CandidateResult(v.ballotNumber(),v.percentage())).toList()));
        assertThat(importer.run(json.valueToTree(Map.of("polls",rows)),false,false).created()).isEqualTo(18);
        var before=jdbc.queryForList("SELECT to_jsonb(p)::text FROM polls p ORDER BY id",String.class);
        var csv=getClass().getResourceAsStream("/discovery/tse-scope-observed.csv").readAllBytes();var bytes=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)){zip.putNextEntry(new java.util.zip.ZipEntry("pesquisa_eleitoral_2026_BRASIL.csv"));zip.write(csv);zip.closeEntry();}
        when(client.download()).thenReturn(bytes.toByteArray());var first=service.sync();var second=service.sync();
        assertThat(first.scanned()).isEqualTo(3);assertThat(first.eligible()).isEqualTo(1);assertThat(first.created()).isEqualTo(1);
        assertThat(second.created()).isZero();assertThat(second.unchanged()).isEqualTo(1);
        assertThat(service.list(null,null,null).items()).extracting(d->d.registrationNumber).containsExactly("BR-09709/2026");
        assertThat(service.list(null,null,null).items().getFirst().status).isEqualTo(Status.IMPORTED);
        assertThat(jdbc.queryForList("SELECT to_jsonb(p)::text FROM polls p ORDER BY id",String.class)).isEqualTo(before);
        assertThat(count("polls")).isEqualTo(18);assertThat(count("aggregate_snapshots")).isZero();
    }
    @Test void futurePublicationIsStagedForOperationalPlanning(){var e=entry("BR-09999/2026","Datafolha");service.stage(List.of(new TsePollRegistryParser.Entry(e.registrationNumber(),e.pollsterName(),e.legalName(),e.fieldStart(),e.fieldEnd(),LocalDate.parse("2026-10-25"),e.sampleSize(),e.methodology(),e.metadata())));assertThat(count("poll_discoveries")).isEqualTo(1);}
    @Test void editOnlyStagingAndDryRunUsesExistingImporter(){var d=ready();assertThat(d.reviewedAt).isNotNull();var report=service.dryRun(d.id);assertThat(report.created()).isEqualTo(1);
        verify(importer).run(any(),eq(true),eq(false));assertThat(service.get(d.id).dryRunHash).hasSize(64);assertThat(count("polls")).isZero();assertThat(count("aggregate_snapshots")).isZero();}
    @Test void changesAfterPreviewInvalidateApprovalEvenNotesOnly(){var d=preview();String hash=d.dryRunHash;service.review(d.id,review("Revisão alterada"));assertThat(service.get(d.id).dryRunHash).isNull();
        assertThatThrownBy(()->service.approve(d.id,hash,true)).hasMessageContaining("dry-run");assertThat(count("polls")).isZero();service.dryRun(d.id);assertThat(service.get(d.id).dryRunHash).isNotEqualTo(hash);}
    @Test void requiresExplicitApprovalAndCurrentPayloadHash(){var d=preview();assertThatThrownBy(()->service.approve(d.id,d.dryRunHash,false)).hasMessageContaining("aprovação");
        assertThatThrownBy(()->service.approve(d.id,"wrong",true)).hasMessageContaining("hash");assertThat(count("aggregate_snapshots")).isZero();}
    @Test void approvedImportAtomicallyCreatesPollAndFourSnapshotsAndPreservesAudit(){var d=preview();var result=service.approve(d.id,d.dryRunHash,true);
        assertThat(result.report().created()).isEqualTo(1);assertThat(result.snapshots()).isEqualTo(4);assertThat(count("polls")).isEqualTo(1);assertThat(count("aggregate_snapshots")).isEqualTo(4);
        assertThat(service.get(d.id).status).isEqualTo(Status.IMPORTED);assertThat(service.get(d.id).importedPollId).isEqualTo(result.importedPollId());assertThat(count("poll_discovery_events")).isEqualTo(4);
        assertThat(service.get(d.id).importReport.dryRun()).isFalse();assertThat(service.get(d.id).snapshotIds).hasSize(4);
        assertThatThrownBy(()->service.approve(d.id,d.dryRunHash,true)).hasMessageContaining("já importado");assertThat(count("aggregate_snapshots")).isEqualTo(4);}
    @Test void snapshotFailureRollsBackPollAndStagingImport(){var d=preview();doThrow(new IllegalArgumentException("synthetic snapshot failure")).when(snapshots).create(any(LocalDate.class));
        assertThatThrownBy(()->service.approve(d.id,d.dryRunHash,true)).hasMessageContaining("snapshot failure");assertThat(count("polls")).isZero();assertThat(count("aggregate_snapshots")).isZero();assertThat(service.get(d.id).status).isEqualTo(Status.READY_FOR_REVIEW);}
    @Test void validationFailureNeverCreatesSnapshots(){var d=ready();var r=review(null);service.review(d.id,new Review(r.pollsterSlug(),r.fieldStart(),r.fieldEnd(),r.publishedAt(),r.sampleSize(),r.marginOfError(),r.methodology(),r.sourceUrl(),VoteBasis.TOTAL,
        List.of(new Result(22,new BigDecimal("70")),new Result(13,new BigDecimal("70"))),null,true,true));
        assertThat(service.dryRun(d.id).failed()).isEqualTo(1);assertThat(service.get(d.id).status).isEqualTo(Status.INVALID);verify(snapshots,never()).create(any(LocalDate.class));assertThat(count("polls")).isZero();}
    @Test void existingRegistrationIsMarkedImportedAndNeverOverwritten(){var d=preview();service.approve(d.id,d.dryRunHash,true);jdbc.update("DELETE FROM poll_discovery_events");jdbc.update("DELETE FROM poll_discoveries");
        var staged=detect();assertThat(staged.status).isEqualTo(Status.IMPORTED);assertThat(staged.importedPollId).isNotNull();assertThatThrownBy(()->service.review(staged.id,review(null))).hasMessageContaining("terminal");assertThat(count("polls")).isEqualTo(1);}
    @Test void registryChangesInvalidateAndRetainOriginalMetadata(){var d=preview();var e=entry(d.registrationNumber,"Datafolha");var metadata=new HashMap<>(e.metadata());metadata.put("changed","true");
        service.stage(List.of(new TsePollRegistryParser.Entry(e.registrationNumber(),e.pollsterName(),e.legalName(),e.fieldStart(),e.fieldEnd(),e.expectedPublicationDate(),e.sampleSize(),e.methodology(),metadata)));
        var after=service.get(d.id);assertThat(after.dryRunHash).isNull();assertThat(after.tseMetadata).doesNotContainKey("changed");assertThat(after.latestTseMetadata).containsKey("changed");assertThat(after.secondRoundConfirmed).isFalse();}
    @Test void generationTimestampAloneDoesNotInvalidateReview(){var d=preview();var e=entry(d.registrationNumber,"Datafolha");var m=new HashMap<>(e.metadata());m.put("DT_GERACAO","24/10/2026");
        service.stage(List.of(new TsePollRegistryParser.Entry(e.registrationNumber(),e.pollsterName(),e.legalName(),e.fieldStart(),e.fieldEnd(),e.expectedPublicationDate(),e.sampleSize(),e.methodology(),m)));
        assertThat(service.get(d.id).dryRunHash).isEqualTo(d.dryRunHash);}
    @Test void ignoredRecordsRemainAuditableAndDoNotReopenOnSync(){var d=detect();service.ignore(d.id,"Ignorado pelo operador");service.stage(List.of(entry(d.registrationNumber,"Datafolha")));assertThat(service.get(d.id).status).isEqualTo(Status.IGNORED);assertThat(count("polls")).isZero();}
    @Test void humanEligibilityAndKnownBallotsAreMandatory(){var d=detect();var r=review(null);assertThatThrownBy(()->service.review(d.id,new Review(r.pollsterSlug(),r.fieldStart(),r.fieldEnd(),r.publishedAt(),r.sampleSize(),r.marginOfError(),r.methodology(),r.sourceUrl(),r.voteBasis(),r.candidateResults(),null,false,true))).hasMessageContaining("Confirme");}
    @Test void filtersSummaryAndAdminProtection() throws Exception {var d=detect();assertThat(service.list(Status.AWAITING_RESULTS,LocalDate.parse("2026-10-22"),"datafolha").items()).hasSize(1);
        assertThat(service.list(Status.IMPORTED,null,null).items()).isEmpty();assertThat(service.list(null,null,null).detectedToday()).isEqualTo(1);
        mvc.perform(get("/api/admin/poll-discoveries")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/poll-discoveries/sync")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/poll-discoveries/"+d.id).header("X-Admin-Key","discovery-test-key")).andExpect(status().isOk());
        mvc.perform(put("/api/admin/poll-discoveries/"+d.id+"/results").header("X-Admin-Key","discovery-test-key").contentType("application/json").content(json.writeValueAsString(review(null)))).andExpect(status().isOk());}
    @Test void approvalInOccupiedSnapshotMinuteDoesNotImportOrReturnOldSnapshots(){
        var first=preview();service.approve(first.id,first.dryRunHash,true);
        service.stage(List.of(entry("BR-09999/2026","Datafolha")));var next=service.list(null,null,null).items().stream().filter(d->d.registrationNumber.equals("BR-09999/2026")).findFirst().orElseThrow();
        service.review(next.id,review(null));service.dryRun(next.id);var approved=service.get(next.id);
        assertThatThrownBy(()->service.approve(next.id,approved.dryRunHash,true)).hasMessageContaining("próximo minuto");
        assertThat(count("polls")).isEqualTo(1);assertThat(count("aggregate_snapshots")).isEqualTo(4);
    }
    @Test void concurrentApprovalsCreateOnePollAndOneSetOfSnapshots() throws Exception {
        var d=preview();var first=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return service.approve(d.id,d.dryRunHash,true).snapshots();}catch(IllegalArgumentException e){return 0;}});
        var second=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return service.approve(d.id,d.dryRunHash,true).snapshots();}catch(IllegalArgumentException e){return 0;}});
        assertThat(first.get(30,java.util.concurrent.TimeUnit.SECONDS)+second.get(30,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(4);
        assertThat(count("polls")).isEqualTo(1);assertThat(count("aggregate_snapshots")).isEqualTo(4);
    }

}
