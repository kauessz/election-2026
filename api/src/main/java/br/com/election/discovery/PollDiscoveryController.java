package br.com.election.discovery;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import static br.com.election.discovery.DiscoveryModels.*;
@RestController @RequestMapping("/api/admin/poll-discoveries")
public class PollDiscoveryController {
    private final PollDiscoveryService service;
    public PollDiscoveryController(PollDiscoveryService service){this.service=service;}
    @PostMapping("/sync") public Sync sync(){return service.sync();}
    @GetMapping public Inbox list(@RequestParam(required=false) Status status,@RequestParam(required=false) LocalDate date,@RequestParam(required=false) LocalDate expectedPublicationDate,@RequestParam(required=false) String pollster){
        if(date!=null&&expectedPublicationDate!=null&&!date.equals(expectedPublicationDate))throw new IllegalArgumentException("Filtros de divulgação conflitantes.");
        return service.list(status,expectedPublicationDate==null?date:expectedPublicationDate,pollster);
    }
    @GetMapping("/{id}") public Discovery get(@PathVariable UUID id){return service.get(id);}
    @PutMapping("/{id}/results") public Discovery review(@PathVariable UUID id,@Valid @RequestBody Review request){return service.review(id,request);}
    @PostMapping("/{id}/dry-run") public br.com.election.readiness.ImportModels.Report dryRun(@PathVariable UUID id){return service.dryRun(id);}
    public record Approval(@NotBlank String payloadHash,boolean approved){}
    @PostMapping("/{id}/import") public Imported approve(@PathVariable UUID id,@Valid @RequestBody Approval approval){return service.approve(id,approval.payloadHash(),approval.approved());}
    @PostMapping("/{id}/ignore") public Discovery ignore(@PathVariable UUID id,@Valid @RequestBody Ignore request){return service.ignore(id,request.notes());}
}
