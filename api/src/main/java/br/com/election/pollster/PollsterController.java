package br.com.election.pollster;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
@RestController
public class PollsterController {
    private final PollsterService service;
    public PollsterController(PollsterService service) { this.service = service; }
    @GetMapping("/api/pollsters") public List<PollsterResponse> list() { return service.list(); }
    @PostMapping("/api/admin/pollsters") public ResponseEntity<PollsterResponse> create(@Valid @RequestBody PollsterRequest request) {
        return ResponseEntity.created(URI.create("/api/pollsters")).body(service.create(request));
    }
}
