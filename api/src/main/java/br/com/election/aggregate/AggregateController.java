package br.com.election.aggregate;

import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;

@RestController
public class AggregateController {
    private final AggregateService service;
    private final AggregateSnapshotService snapshots;
    public AggregateController(AggregateService service, AggregateSnapshotService snapshots) { this.service = service; this.snapshots = snapshots; }

    @GetMapping("/api/aggregates")
    public List<AggregateResponse> all(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        return service.all(reference(referenceDate));
    }
    @GetMapping("/api/aggregates/simple")
    public AggregateResponse simple(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        return service.one(AggregateMethod.SIMPLE, reference(referenceDate));
    }
    @GetMapping("/api/aggregates/recency")
    public AggregateResponse recency(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        return service.one(AggregateMethod.RECENCY, reference(referenceDate));
    }
    @GetMapping("/api/aggregates/weighted")
    public AggregateResponse weighted(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        return service.one(AggregateMethod.WEIGHTED, reference(referenceDate));
    }
    @GetMapping("/api/aggregates/calibrated")
    public AggregateResponse calibrated(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        return service.one(AggregateMethod.CALIBRATED, reference(referenceDate));
    }
    @GetMapping("/api/aggregates/history")
    public List<SnapshotResponse> history(@RequestParam(required=false) AggregateMethod method) { return snapshots.history(method); }

    @PostMapping("/api/admin/aggregates/snapshot")
    public List<SnapshotResponse> snapshot(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        return snapshots.create(reference(referenceDate));
    }
    private LocalDate reference(LocalDate date) { return date == null ? service.today() : date; }
}
