package br.com.election.pollster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import java.util.List;
@Service
public class PollsterService {
    private final PollsterRepository repository;
    public PollsterService(PollsterRepository repository) { this.repository = repository; }
    @Transactional(readOnly=true) public List<PollsterResponse> list() { return repository.findAll(Sort.by("name")).stream().map(PollsterResponse::from).toList(); }
    @Transactional public PollsterResponse create(PollsterRequest request) {
        Pollster p = new Pollster(); p.setName(request.name().trim()); p.setSlug(request.slug()); p.setWebsiteUrl(request.websiteUrl());
        p.setActive(!Boolean.FALSE.equals(request.active())); return PollsterResponse.from(repository.save(p));
    }
}
