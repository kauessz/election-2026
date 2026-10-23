package br.com.election.poll;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
@RestController
public class PollController {
    private final PollService service;
    public PollController(PollService service) { this.service = service; }
    @GetMapping("/api/polls") public List<PollResponse> list() { return service.list(); }
    @GetMapping("/api/polls/latest") public List<PollResponse> latest() { return service.latest(); }
    @GetMapping("/api/polls/{id}") public PollResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping("/api/admin/polls") public ResponseEntity<PollResponse> create(@Valid @RequestBody PollRequest request) {
        PollResponse result = service.create(request); return ResponseEntity.created(URI.create("/api/polls/" + result.id())).body(result);
    }
    @PutMapping("/api/admin/polls/{id}") public PollResponse update(@PathVariable UUID id, @Valid @RequestBody PollRequest request) { return service.update(id, request); }
    @DeleteMapping("/api/admin/polls/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
}
