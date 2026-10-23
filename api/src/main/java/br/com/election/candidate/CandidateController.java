package br.com.election.candidate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
@RestController
public class CandidateController {
    private final CandidateService service;
    public CandidateController(CandidateService service) { this.service = service; }
    @GetMapping("/api/candidates") public List<CandidateResponse> list() { return service.list(); }
    @PostMapping("/api/admin/candidates") public ResponseEntity<CandidateResponse> create(@Valid @RequestBody CandidateRequest request) {
        CandidateResponse result = service.create(request); return ResponseEntity.created(URI.create("/api/candidates")).body(result);
    }
}
