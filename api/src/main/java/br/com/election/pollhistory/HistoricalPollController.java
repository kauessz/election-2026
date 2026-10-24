package br.com.election.pollhistory;

import br.com.election.readiness.ImportModels.Report;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.List;

@RestController
public class HistoricalPollController {
    private final HistoricalPollStore store;
    private final HistoricalPollImportService imports;
    public HistoricalPollController(HistoricalPollStore store, HistoricalPollImportService imports) {
        this.store=store; this.imports=imports;
    }
    @GetMapping("/api/poll-history")
    public List<HistoricalPollModels.Response> list(@RequestParam(required=false) Integer round,
        @RequestParam(defaultValue="BR-PRESIDENT-2026") String electionKey) { return store.list(electionKey,round); }
    @PostMapping("/api/admin/poll-history/import")
    public Report importPolls(@RequestBody JsonNode body,@RequestParam(defaultValue="true") boolean dryRun) {
        return imports.run(body,dryRun);
    }
}
