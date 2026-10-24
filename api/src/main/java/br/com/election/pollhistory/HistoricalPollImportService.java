package br.com.election.pollhistory;

import br.com.election.pollster.PollsterRepository;
import br.com.election.readiness.ImportModels.Detail;
import br.com.election.readiness.ImportModels.Report;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.security.*;
import java.time.Clock;
import java.util.*;
import static br.com.election.pollhistory.HistoricalPollModels.*;

@Service
public class HistoricalPollImportService {
    private final HistoricalPollValidation validation;
    private final HistoricalPollStore store;
    private final PollsterRepository pollsters;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;
    public HistoricalPollImportService(HistoricalPollValidation validation, HistoricalPollStore store,
        PollsterRepository pollsters, JsonMapper json, JdbcTemplate jdbc, Clock clock, PlatformTransactionManager manager) {
        this.validation=validation; this.store=store; this.pollsters=pollsters; this.json=json; this.jdbc=jdbc; this.clock=clock;
        tx=new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Report run(JsonNode body, boolean dryRun) {
        JsonNode rows = body == null ? null : body.isArray() ? body : body.get("polls");
        if (rows == null || !rows.isArray() || rows.size()>1000)
            throw new IllegalArgumentException("Informe polls como array, com no máximo 1000 registros.");
        List<Detail> details = new ArrayList<>(); Map<String,String> simulated = new HashMap<>();
        for (int i=0; i<rows.size(); i++) {
            final int index=i; final JsonNode raw=rows.get(i);
            try {
                details.add(tx.execute(status -> {
                    jdbc.queryForObject("SELECT pg_advisory_xact_lock(20260701) IS NULL",Boolean.class);
                    var detail = row(raw,index,dryRun,simulated);
                    if (dryRun) status.setRollbackOnly();
                    return detail;
                }));
            } catch (RuntimeException e) {
                details.add(new Detail(index,"FAILED",null,null,null,e instanceof DataAccessException
                    ? "Conflito de dados; nenhuma alteração neste registro histórico." : Objects.toString(e.getMessage(),"Registro inválido.")));
            }
        }
        return new Report(dryRun,count(details,"CREATED"),count(details,"UPDATED"),count(details,"UNCHANGED"),count(details,"FAILED"),List.copyOf(details));
    }
    private Detail row(JsonNode raw,int index,boolean dryRun,Map<String,String> simulated) {
        if (raw == null || !raw.isObject()) throw new IllegalArgumentException("Registro deve ser um objeto.");
        var candidateResults = raw.get("candidateResults");
        if (candidateResults != null && candidateResults.isArray()) for (var c : candidateResults) {
            if (c.isObject() && c.has("normalizedPercentage"))
                throw new IllegalArgumentException("normalizedPercentage é calculado pelo backend quando autorizado metodologicamente.");
        }
        var prepared=validation.prepare(json.treeToValue(raw,Request.class)); var r=prepared.request();
        var matches=pollsters.findAll().stream().filter(p -> p.getSlug().equals(r.pollsterSlug())).toList();
        if (matches.size()!=1) throw new IllegalArgumentException("pollsterSlug inexistente ou ambíguo: "+r.pollsterSlug());
        var pollster=matches.getFirst();
        String identity=r.registrationNumber()!=null ? "REG:"+r.registrationNumber() : "DATE:"+r.fieldEnd()+"|"+r.publishedAt();
        String key=r.electionKey()+"|"+r.round()+"|"+pollster.getId()+"|"+identity;
        String hash=hash(json.writeValueAsBytes(r));
        var existing=store.find(r.electionKey(),r.round(),pollster.getId(),identity);
        String previous=dryRun && simulated.containsKey(key) ? simulated.get(key) : existing == null ? null : existing.hash();
        String action=previous==null ? "CREATED" : previous.equals(hash) ? "UNCHANGED" : "UPDATED";
        UUID id=existing==null ? null : existing.id();
        if (!dryRun && !action.equals("UNCHANGED")) {
            if (id==null) id=UUID.randomUUID();
            store.save(id,pollster.getId(),identity,hash,prepared,clock.instant());
        }
        if (dryRun) simulated.put(key,hash);
        return new Detail(index,action,id,key,prepared.results(),null);
    }
    private static long count(List<Detail> details,String status) { return details.stream().filter(d -> status.equals(d.status())).count(); }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 indisponível."); }
    }
}
