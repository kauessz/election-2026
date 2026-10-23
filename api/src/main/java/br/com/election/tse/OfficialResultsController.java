package br.com.election.tse;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;
import java.util.List;
@RestController @RequestMapping("/api/election")
public class OfficialResultsController {
 private final OfficialResultsService service;private final MunicipalityCatalogue catalogue;private final OfficialSnapshotStore snapshots;
 public OfficialResultsController(OfficialResultsService service,MunicipalityCatalogue catalogue,OfficialSnapshotStore snapshots){this.service=service;this.catalogue=catalogue;this.snapshots=snapshots;}
 @GetMapping("/president") public OfficialResultResponse national(){return service.getPresidentNationalResult();}
 @GetMapping("/president/states") public List<OfficialResultResponse> states(){return service.getStates();}
 @GetMapping("/president/states/{uf}") public OfficialResultResponse state(@PathVariable String uf){return service.getState(BrazilState.parse(uf));}
 @GetMapping("/president/municipalities") public List<MunicipalityCatalogue.Municipality> allMunicipalities(){return catalogue.all();}
 @GetMapping("/president/states/{uf}/municipalities") public List<MunicipalityCatalogue.Municipality> municipalities(@PathVariable String uf){return catalogue.list(BrazilState.parse(uf));}
 @GetMapping("/president/states/{uf}/municipalities/{municipalityCode}") public OfficialResultResponse municipality(@PathVariable String uf,@PathVariable String municipalityCode){return service.getMunicipality(BrazilState.parse(uf),municipalityCode);}
 @GetMapping("/president/history") public List<OfficialElectionResult> history(
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
  @RequestParam(defaultValue="500") int limit){return snapshots.history(from==null?Instant.parse("2026-01-01T00:00:00Z"):from,to==null?Instant.parse("2100-01-01T00:00:00Z"):to,limit);}
 @GetMapping("/source-status") public OfficialResultsService.SourceStatus sourceStatus(){return service.sourceStatus();}
}
