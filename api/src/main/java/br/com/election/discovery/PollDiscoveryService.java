package br.com.election.discovery;

import br.com.election.aggregate.AggregateSnapshotService;
import br.com.election.readiness.*;
import br.com.election.pollster.PollsterRepository;
import br.com.election.pollster.Pollster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import static br.com.election.discovery.DiscoveryModels.*;

@Service
public class PollDiscoveryService {
    private final PollDiscoveryStore store; private final TsePollRegistryClient client;private final TsePollRegistryParser parser;
    private final PollsterRepository institutes;private final BatchImportService imports;private final AggregateSnapshotService snapshots;
    private final JdbcTemplate jdbc;private final JsonMapper json;private final Clock clock;
    private final org.springframework.transaction.support.TransactionTemplate staging;
    private final DiscoveryConfiguration.Properties properties;
    public PollDiscoveryService(PollDiscoveryStore store,TsePollRegistryClient client,TsePollRegistryParser parser,PollsterRepository institutes,
        BatchImportService imports,AggregateSnapshotService snapshots,JdbcTemplate jdbc,JsonMapper json,Clock clock,org.springframework.transaction.PlatformTransactionManager manager,DiscoveryConfiguration.Properties properties){
        this.store=store;this.client=client;this.parser=parser;this.institutes=institutes;this.imports=imports;this.snapshots=snapshots;this.jdbc=jdbc;this.json=json;this.clock=clock;
        this.staging=new org.springframework.transaction.support.TransactionTemplate(manager);
        this.properties=properties;
    }
    public Sync sync(){var parsed=parser.inspect(client.download());return staging.execute(status->{var result=stage(parsed.entries());return new Sync(parsed.scanned(),result.created(),result.updated(),result.unchanged(),result.eligible());});}
    // Public for a deterministic local-fixture test; never exposed as an upload/URL endpoint.
    @Transactional public Sync stage(List<TsePollRegistryParser.Entry> entries){
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(20260801) IS NULL",Boolean.class);
        int created=0,updated=0,unchanged=0,eligible=0;var pollsters=institutes.findAll();
        for(var item:entries){
            if(item.expectedPublicationDate().isBefore(properties.operationalFrom()))continue;
            eligible++;
            Discovery d=store.registration(item.registrationNumber());boolean fresh=d==null;
            var relevantMetadata=new TreeMap<>(item.metadata());relevantMetadata.remove("DT_GERACAO");relevantMetadata.remove("HH_GERACAO");
            String registryHash=hash(json.valueToTree(relevantMetadata));
            if(fresh){d=new Discovery();d.id=UUID.randomUUID();d.registrationNumber=item.registrationNumber();d.pollsterName=item.pollsterName();
                d.tseMetadata=item.metadata();d.detectedAt=clock.instant();d.status=Status.DETECTED;}
            var existing=existingPoll(item.registrationNumber());
            String mapped=d.pollsterSlug==null?mapPollster(item,pollsters):d.pollsterSlug;
            if(!fresh && Objects.equals(d.registryHash,registryHash) && Objects.equals(d.pollsterSlug,mapped) && (existing==null || d.status==Status.IMPORTED)) {unchanged++;continue;}
            if(d.status==Status.IMPORTED || d.status==Status.IGNORED){
                if(d.pollsterSlug==null)d.pollsterSlug=mapped;
                if(existing!=null){d.status=Status.IMPORTED;d.importedPollId=existing;}
                d.latestTseMetadata=item.metadata();d.registryHash=registryHash;d.updatedAt=clock.instant();store.save(d,"REGISTRY_REFRESH");updated++;continue;
            }
            d.latestTseMetadata=item.metadata();d.registryHash=registryHash;d.pollsterName=item.pollsterName();
            d.fieldStart=item.fieldStart();d.fieldEnd=item.fieldEnd();d.expectedPublicationDate=item.expectedPublicationDate();
            d.sampleSize=item.sampleSize();d.methodology=item.methodology();
            if(d.pollsterSlug==null)d.pollsterSlug=mapped;
            invalidate(d);d.secondRoundConfirmed=false;d.publicationAllowedConfirmed=false;
            if(existing!=null){d.status=Status.IMPORTED;d.importedPollId=existing;}
            else d.status=d.pollsterSlug==null?Status.UNMAPPED_POLLSTER:Status.AWAITING_RESULTS;
            d.updatedAt=clock.instant();store.save(d,fresh?"DETECTED":"REGISTRY_CHANGED");if(fresh)created++;else updated++;
        }
        return new Sync(entries.size(),created,updated,unchanged,eligible);
    }
    private String mapPollster(TsePollRegistryParser.Entry entry,List<Pollster> pollsters){
        Set<String> names=new HashSet<>(List.of(TsePollRegistryParser.normalize(entry.pollsterName()),TsePollRegistryParser.normalize(entry.legalName())));
        var found=pollsters.stream().filter(p->names.contains(TsePollRegistryParser.normalize(p.getName()))||names.contains(TsePollRegistryParser.normalize(p.getSlug()))).toList();
        if(found.size()==1)return found.getFirst().getSlug();
        if(!found.isEmpty())return null;
        var aliases=names.stream().map(PollsterAliases::slug).filter(Objects::nonNull).distinct().toList();
        if(aliases.size()!=1)return null;
        return pollsters.stream().filter(p->p.getSlug().equals(aliases.getFirst())&&p.getActive()).map(Pollster::getSlug).findFirst().orElse(null);
    }
    public Inbox list(Status status,LocalDate date,String pollster){
        var all=store.list();Map<Status,Long> counts=new EnumMap<>(Status.class);for(var s:Status.values())counts.put(s,all.stream().filter(d->d.status==s).count());
        return new Inbox(all.stream().filter(d->status==null||d.status==status).filter(d->date==null||date.equals(d.expectedPublicationDate))
            .filter(d->pollster==null||pollster.isBlank()||pollster.equals(d.pollsterSlug)||TsePollRegistryParser.normalize(d.pollsterName).contains(TsePollRegistryParser.normalize(pollster)))
            .sorted(Comparator.comparingInt(this::priority).thenComparing(d->priority(d)==1?d.expectedPublicationDate.toEpochDay():-d.expectedPublicationDate.toEpochDay()).thenComparing(d->d.registrationNumber)).toList(),
            counts,all.stream().filter(d->d.detectedAt.atZone(DiscoveryConfiguration.ZONE).toLocalDate().equals(today())).count(),
            all.stream().filter(d->today().equals(d.expectedPublicationDate)).count());
    }
    private int priority(Discovery d){if(today().equals(d.expectedPublicationDate))return 0;if(d.expectedPublicationDate.isAfter(today()))return 1;return d.status==Status.IMPORTED||d.status==Status.IGNORED?3:2;}
    public Discovery get(UUID id){return store.get(id,false);}
    @Transactional public Discovery review(UUID id,Review r){
        Discovery d=store.get(id,true);editable(d);
        if(institutes.findAll().stream().noneMatch(p->p.getSlug().equals(r.pollsterSlug())&&p.getActive()))throw new IllegalArgumentException("Associe a um instituto existente e ativo; cadastro nunca é automático.");
        if(!r.secondRoundConfirmed()||!r.publicationAllowedConfirmed())throw new IllegalArgumentException("Confirme no PesqEle o segundo turno e a permissão de divulgação, inclusive eventuais decisões judiciais.");
        if(r.fieldStart().isBefore(LocalDate.of(2026,10,5))||r.fieldEnd().isAfter(LocalDate.of(2026,10,25))||r.fieldEnd().isBefore(r.fieldStart()))throw new IllegalArgumentException("Campo fora da disputa presidencial do segundo turno.");
        if(r.publishedAt().isAfter(clock.instant())||r.publishedAt().atZone(DiscoveryConfiguration.ZONE).toLocalDate().isBefore(r.fieldEnd())
            ||r.publishedAt().atZone(DiscoveryConfiguration.ZONE).toLocalDate().isBefore(d.expectedPublicationDate))throw new IllegalArgumentException("Publicação real deve ser verificável, não futura e posterior ao campo e à data permitida pelo cadastro.");
        var ballots=r.candidateResults().stream().map(Result::ballotNumber).sorted().toList();
        if(!ballots.equals(List.of(13,22)))throw new IllegalArgumentException("Informe exatamente os resultados dos candidatos 13 e 22.");
        d.pollsterSlug=r.pollsterSlug();d.fieldStart=r.fieldStart();d.fieldEnd=r.fieldEnd();d.publishedAt=r.publishedAt();
        d.sampleSize=r.sampleSize();d.marginOfError=r.marginOfError();d.methodology=r.methodology();d.sourceUrl=r.sourceUrl();d.voteBasis=r.voteBasis();
        d.candidateResults=r.candidateResults().stream().sorted(Comparator.comparing(Result::ballotNumber))
            .map(v->new ImportModels.CandidateResult(v.ballotNumber(),v.percentage().stripTrailingZeros())).toList();
        d.notes=r.notes();d.secondRoundConfirmed=true;d.publicationAllowedConfirmed=true;d.reviewedAt=clock.instant();d.updatedAt=clock.instant();
        invalidate(d);d.status=Status.READY_FOR_REVIEW;store.save(d,"OPERATOR_REVIEW");return d;
    }
    @Transactional public ImportModels.Report dryRun(UUID id){
        Discovery d=store.get(id,true);ready(d);var payload=payload(d);
        var report=imports.run(payload,true,false);d.dryRunReport=report;d.dryRunAt=clock.instant();
        d.dryRunHash=report.failed()==0?reviewHash(d,payload):null;d.updatedAt=clock.instant();
        if(report.failed()>0)d.status=Status.INVALID;store.save(d,"DRY_RUN");return report;
    }
    @Transactional public Imported approve(UUID id,String expectedHash,boolean approved){
        Discovery d=store.get(id,true);
        if(d.status==Status.IMPORTED)throw new IllegalArgumentException("Registro já importado; não serão criados novos snapshots.");
        ready(d);var payload=payload(d);String currentHash=reviewHash(d,payload);
        if(!approved || d.dryRunHash==null || !d.dryRunHash.equals(currentHash)||!currentHash.equals(expectedHash)
            ||d.dryRunReport==null||d.dryRunReport.failed()!=0)throw new IllegalArgumentException("Exige aprovação explícita do hash atual após dry-run bem-sucedido. Revise e simule novamente.");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(20260501) IS NULL",Boolean.class);
        if(existingPoll(d.registrationNumber)!=null)throw new IllegalArgumentException("Registro já existe em polls; sincronize a inbox, sem sobrescrever a pesquisa.");
        jdbc.execute("LOCK TABLE aggregate_snapshots IN SHARE ROW EXCLUSIVE MODE");
        // Existing snapshot service coalesces method/date/minute. Require an unused minute so each
        // successful inbox import gets four NEW audit snapshots rather than returning stale ones.
        if(jdbc.queryForObject("SELECT count(*) FROM aggregate_snapshots WHERE reference_date=? AND snapshot_minute=?",Integer.class,
            today(),java.sql.Timestamp.from(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)))>0)
            throw new IllegalArgumentException("Já existem snapshots neste minuto. Aguarde o próximo minuto e repita a aprovação; nenhuma pesquisa foi importada.");
        var report=imports.runInTransaction(payload);
        if(report.failed()!=0 || report.created()!=1 || report.details().getFirst().id()==null)throw new IllegalArgumentException("Importação não aprovada: "+report.details().getFirst().message());
        d.importedPollId=report.details().getFirst().id();var savedSnapshots=snapshots.create(today());int count=savedSnapshots.size();
        d.importReport=report;d.snapshotIds=savedSnapshots.stream().map(br.com.election.aggregate.SnapshotResponse::id).toList();
        d.status=Status.IMPORTED;d.updatedAt=clock.instant();store.save(d,"APPROVED_IMPORT");return new Imported(report,d.importedPollId,count);
    }
    @Transactional public Discovery ignore(UUID id,String notes){Discovery d=store.get(id,true);editable(d);d.status=Status.IGNORED;d.notes=notes;invalidate(d);d.updatedAt=clock.instant();store.save(d,"IGNORED");return d;}
    private void editable(Discovery d){if(d.status==Status.IMPORTED||d.status==Status.IGNORED)throw new IllegalArgumentException("Registro terminal: revisão não permitida.");}
    private void ready(Discovery d){if(d.status!=Status.READY_FOR_REVIEW||!d.secondRoundConfirmed||!d.publicationAllowedConfirmed)throw new IllegalArgumentException("Conclua a revisão administrativa antes de continuar.");
        var active=jdbc.queryForList("SELECT ballot_number FROM candidates WHERE active ORDER BY ballot_number",Integer.class);
        if(!active.equals(List.of(13,22)))throw new IllegalArgumentException("A disputa ativa não corresponde ao cenário presidencial 13/22.");}
    private void invalidate(Discovery d){d.dryRunHash=null;d.dryRunAt=null;d.dryRunReport=null;}
    private JsonNode payload(Discovery d){
        return json.valueToTree(Map.of("polls",List.of(new ImportModels.PollRow(d.pollsterSlug,d.registrationNumber,d.fieldStart,d.fieldEnd,d.publishedAt,
            d.sampleSize,d.marginOfError,d.methodology,d.sourceUrl,d.voteBasis,d.candidateResults))));
    }
    private UUID existingPoll(String reg){var ids=jdbc.queryForList("SELECT id FROM polls WHERE btrim(tse_registration_number)=?",UUID.class,reg);
        if(ids.size()>1)throw new IllegalArgumentException("Registro operacional ambíguo: "+reg);return ids.isEmpty()?null:ids.getFirst();}
    private String hash(JsonNode node){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(node).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private String reviewHash(Discovery d,JsonNode payload){return hash(json.createObjectNode().set("import",payload)
        .put("notes",d.notes).put("reviewedAt",d.reviewedAt.toString()).put("registryHash",d.registryHash));}
    private LocalDate today(){return LocalDate.now(clock.withZone(DiscoveryConfiguration.ZONE));}
}
