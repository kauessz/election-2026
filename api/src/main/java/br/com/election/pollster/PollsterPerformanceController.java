package br.com.election.pollster;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/admin/pollster-performances")
public class PollsterPerformanceController {
    private final PollsterPerformanceService service;
    public PollsterPerformanceController(PollsterPerformanceService service) { this.service = service; }
    @GetMapping public List<PollsterPerformanceResponse> list() { return service.list(); }
    @PostMapping public ResponseEntity<PollsterPerformanceResponse> create(@Valid @RequestBody PollsterPerformanceRequest request) {
        return ResponseEntity.created(URI.create("/api/admin/pollster-performances")).body(service.create(request));
    }
    @PutMapping("/{id}") public PollsterPerformanceResponse update(@PathVariable UUID id, @Valid @RequestBody PollsterPerformanceRequest request) { return service.update(id, request); }
}
