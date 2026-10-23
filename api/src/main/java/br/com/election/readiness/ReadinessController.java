package br.com.election.readiness;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.List;
@RestController
public class ReadinessController {
 private final BatchImportService imports;private final DataQualityService quality;private final FreezeService freezes;private final AggregateAuditService audit;private final PerformanceCalculationService calculations;private final PreElectionComparisonService comparison;
 public ReadinessController(BatchImportService imports,DataQualityService quality,FreezeService freezes,AggregateAuditService audit,PerformanceCalculationService calculations,PreElectionComparisonService comparison){this.imports=imports;this.quality=quality;this.freezes=freezes;this.audit=audit;this.calculations=calculations;this.comparison=comparison;}
 @PostMapping("/api/admin/polls/import") public ImportModels.Report polls(@RequestBody JsonNode body,@RequestParam(defaultValue="false") boolean dryRun){return imports.run(body,dryRun,false);}
 @PostMapping("/api/admin/pollster-performances/import") public ImportModels.Report performances(@RequestBody JsonNode body,@RequestParam(defaultValue="false") boolean dryRun){return imports.run(body,dryRun,true);}
 @PostMapping("/api/admin/pollster-performances/calculate") public PerformanceCalculationService.Calculation calculate(@RequestBody PerformanceCalculationService.Request request){return calculations.calculate(request);}
 @GetMapping("/api/admin/data-quality") public DataQualityService.Report quality(){return quality.check();}
 @GetMapping("/api/aggregates/audit") public List<AggregateAuditService.Audit> audit(){return audit.current();}
 @PostMapping("/api/admin/pre-election-freezes") public PreElectionFreeze create(@Valid @RequestBody FreezeService.Request request){return freezes.create(request);}
 @GetMapping("/api/pre-election-freeze") public org.springframework.http.ResponseEntity<PreElectionFreeze> active(){var f=freezes.active();return f==null?org.springframework.http.ResponseEntity.noContent().build():org.springframework.http.ResponseEntity.ok(f);}
 @GetMapping("/api/election/president/pre-election-comparison") public PreElectionComparisonService.Comparison compare(){return comparison.current();}
}
