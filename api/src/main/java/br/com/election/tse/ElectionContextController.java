package br.com.election.tse;
import org.springframework.web.bind.annotation.*;
@RestController
public class ElectionContextController {
    private final ElectionContextService service;
    public ElectionContextController(ElectionContextService service){this.service=service;}
    @GetMapping("/api/election/president/context") public ElectionContextService.Context current(){return service.current();}
}
