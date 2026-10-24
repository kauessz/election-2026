package br.com.election.pollhistory;

import br.com.election.poll.VoteBasis;
import br.com.election.pollster.PollsterResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static br.com.election.pollhistory.HistoricalPollModels.*;

/** Archive-only SQL. This store never reads or writes the current Poll/Candidate pipeline. */
@Repository
public class HistoricalPollStore {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final JsonMapper json;
    public HistoricalPollStore(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc; this.named = new NamedParameterJdbcTemplate(jdbc); this.json = json;
    }
    public record Existing(UUID id, String hash) {}
    public Existing find(String electionKey, int round, UUID pollster, String identity) {
        return jdbc.query("SELECT id, content_hash FROM historical_polls WHERE election_key=? AND round=? AND pollster_id=? AND identity_key=?",
            (rs, n) -> new Existing(rs.getObject("id", UUID.class), rs.getString("content_hash")), electionKey, round, pollster, identity)
            .stream().findFirst().orElse(null);
    }
    public void save(UUID id, UUID pollster, String identity, String hash, Prepared prepared, Instant now) {
        var r = prepared.request();
        var values = new MapSqlParameterSource().addValue("id", id).addValue("election", r.electionKey())
            .addValue("round", r.round()).addValue("pollster", pollster).addValue("identity", identity)
            .addValue("registration", r.registrationNumber()).addValue("start", r.fieldStart()).addValue("end", r.fieldEnd())
            .addValue("published", Timestamp.from(r.publishedAt())).addValue("sample", r.sampleSize()).addValue("margin", r.marginOfError())
            .addValue("methodology", r.methodology()).addValue("source", r.sourceUrl()).addValue("basis", r.voteBasis().name())
            .addValue("normalization", r.normalization().name()).addValue("complete", r.completeCandidateSet())
            .addValue("expected", json.writeValueAsString(r.expectedCandidateKeys())).addValue("notes", r.notes())
            .addValue("hash", hash).addValue("now", Timestamp.from(now));
        named.update("""
            INSERT INTO historical_polls (id,election_key,round,pollster_id,identity_key,registration_number,
              field_start,field_end,published_at,sample_size,margin_of_error,methodology,source_url,vote_basis,
              normalization,complete_candidate_set,expected_candidate_keys,notes,content_hash,created_at,updated_at)
            VALUES (:id,:election,:round,:pollster,:identity,:registration,:start,:end,:published,:sample,:margin,
              :methodology,:source,:basis,:normalization,:complete,CAST(:expected AS jsonb),:notes,:hash,:now,:now)
            ON CONFLICT (id) DO UPDATE SET field_start=EXCLUDED.field_start,field_end=EXCLUDED.field_end,
              published_at=EXCLUDED.published_at,sample_size=EXCLUDED.sample_size,margin_of_error=EXCLUDED.margin_of_error,
              methodology=EXCLUDED.methodology,source_url=EXCLUDED.source_url,vote_basis=EXCLUDED.vote_basis,
              normalization=EXCLUDED.normalization,complete_candidate_set=EXCLUDED.complete_candidate_set,
              expected_candidate_keys=EXCLUDED.expected_candidate_keys,notes=EXCLUDED.notes,content_hash=EXCLUDED.content_hash,
              updated_at=EXCLUDED.updated_at
            """, values);
        jdbc.update("DELETE FROM historical_poll_results WHERE historical_poll_id=?", id);
        List<Object[]> results = prepared.results().stream().map(c -> new Object[] {UUID.randomUUID(),id,c.candidateKey(),
            c.candidateName(),c.displayName(),c.party(),c.ballotNumber(),c.originalPercentage(),c.normalizedPercentage()}).toList();
        jdbc.batchUpdate("""
            INSERT INTO historical_poll_results (id,historical_poll_id,candidate_key,candidate_name,display_name,party,
              ballot_number,original_percentage,normalized_percentage) VALUES (?,?,?,?,?,?,?,?,?)
            """, results);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public List<Response> list(String electionKey, Integer round) {
        if (electionKey == null || !electionKey.matches("[A-Z0-9]+(-[A-Z0-9]+)*") || electionKey.length()>100)
            throw new IllegalArgumentException("electionKey inválida.");
        if (round != null && round != 1 && round != 2) throw new IllegalArgumentException("round deve ser 1 ou 2.");
        Map<UUID,List<Result>> grouped = new HashMap<>();
        jdbc.query("""
            SELECT r.* FROM historical_poll_results r JOIN historical_polls h ON h.id=r.historical_poll_id
            WHERE h.election_key=? AND (CAST(? AS int) IS NULL OR h.round=?) ORDER BY r.candidate_key
            """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> grouped.computeIfAbsent(rs.getObject("historical_poll_id",UUID.class), k -> new ArrayList<>())
            .add(new Result(rs.getString("candidate_key"),rs.getString("candidate_name"),rs.getString("display_name"),rs.getString("party"),
                rs.getObject("ballot_number",Integer.class),rs.getBigDecimal("original_percentage"),rs.getBigDecimal("normalized_percentage"))), electionKey, round, round);
        return jdbc.query("""
            SELECT h.*,p.name AS p_name,p.slug AS p_slug,p.website_url AS p_website,p.active AS p_active
            FROM historical_polls h JOIN pollsters p ON p.id=h.pollster_id
            WHERE h.election_key=? AND (CAST(? AS int) IS NULL OR h.round=?) ORDER BY h.published_at,h.id
            """, (rs,n) -> new Response(rs.getObject("id",UUID.class),rs.getString("election_key"),rs.getInt("round"),
                new PollsterResponse(rs.getObject("pollster_id",UUID.class),rs.getString("p_name"),rs.getString("p_slug"),rs.getString("p_website"),rs.getBoolean("p_active")),
                rs.getString("registration_number"),rs.getObject("field_start",java.time.LocalDate.class),rs.getObject("field_end",java.time.LocalDate.class),
                rs.getTimestamp("published_at").toInstant(),rs.getObject("sample_size",Integer.class),rs.getBigDecimal("margin_of_error"),
                rs.getString("methodology"),rs.getString("source_url"),VoteBasis.valueOf(rs.getString("vote_basis")),
                Normalization.valueOf(rs.getString("normalization")),rs.getBoolean("complete_candidate_set"),
                json.readValue(rs.getString("expected_candidate_keys"),json.getTypeFactory().constructCollectionType(List.class,String.class)),
                rs.getString("notes"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),
                List.copyOf(grouped.getOrDefault(rs.getObject("id",UUID.class),List.of()))), electionKey,round,round);
    }
}
