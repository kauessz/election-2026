package br.com.election.discovery;

import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import br.com.election.common.NotFoundException;
import static br.com.election.discovery.DiscoveryModels.*;

@Repository
public class PollDiscoveryStore {
    private final JdbcTemplate jdbc;private final JsonMapper json;
    public PollDiscoveryStore(JdbcTemplate jdbc,JsonMapper json){this.jdbc=jdbc;this.json=json;}
    public List<Discovery> list(){return jdbc.query("SELECT payload::text FROM poll_discoveries ORDER BY detected_at DESC,id",(rs,n)->json.readValue(rs.getString(1),Discovery.class));}
    public Discovery get(UUID id,boolean locked){return jdbc.query("SELECT payload::text FROM poll_discoveries WHERE id=?"+(locked?" FOR UPDATE":""),
        (rs,n)->json.readValue(rs.getString(1),Discovery.class),id).stream().findFirst().orElseThrow(()->new NotFoundException("Descoberta não encontrada."));}
    public Discovery registration(String reg){return jdbc.query("SELECT payload::text FROM poll_discoveries WHERE registration_number=? FOR UPDATE",
        (rs,n)->json.readValue(rs.getString(1),Discovery.class),reg).stream().findFirst().orElse(null);}
    public void save(Discovery d,String event){
        jdbc.update("""
            INSERT INTO poll_discoveries(id,registration_number,pollster_name,pollster_slug,field_start,field_end,expected_publication_date,
            sample_size,margin_of_error,methodology,tse_metadata,source_url,vote_basis,candidate_results,status,detected_at,updated_at,reviewed_at,imported_poll_id,notes,payload)
            VALUES(?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?::jsonb,?,?,?,?,?,?,?::jsonb)
            ON CONFLICT(id) DO UPDATE SET pollster_name=excluded.pollster_name,pollster_slug=excluded.pollster_slug,field_start=excluded.field_start,field_end=excluded.field_end,
            expected_publication_date=excluded.expected_publication_date,sample_size=excluded.sample_size,margin_of_error=excluded.margin_of_error,
            methodology=excluded.methodology,source_url=excluded.source_url,vote_basis=excluded.vote_basis,candidate_results=excluded.candidate_results,
            status=excluded.status,updated_at=excluded.updated_at,reviewed_at=excluded.reviewed_at,imported_poll_id=excluded.imported_poll_id,notes=excluded.notes,payload=excluded.payload
            """,d.id,d.registrationNumber,d.pollsterName,d.pollsterSlug,d.fieldStart,d.fieldEnd,d.expectedPublicationDate,d.sampleSize,d.marginOfError,
            d.methodology,json.writeValueAsString(d.tseMetadata),d.sourceUrl,d.voteBasis==null?null:d.voteBasis.name(),d.candidateResults==null?null:json.writeValueAsString(d.candidateResults),
            d.status.name(),timestamp(d.detectedAt),timestamp(d.updatedAt),timestamp(d.reviewedAt),d.importedPollId,d.notes,json.writeValueAsString(d));
        jdbc.update("INSERT INTO poll_discovery_events VALUES(?,?,?,?,?::jsonb)",UUID.randomUUID(),d.id,event,timestamp(d.updatedAt),json.writeValueAsString(d));
    }
    private java.sql.Timestamp timestamp(java.time.Instant instant){return instant==null?null:java.sql.Timestamp.from(instant);}
}
