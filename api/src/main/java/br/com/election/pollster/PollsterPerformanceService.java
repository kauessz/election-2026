package br.com.election.pollster;

import br.com.election.aggregate.HistoricalScoreService;
import br.com.election.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.RoundingMode;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PollsterPerformanceService {
    private final PollsterPerformanceRepository repository;
    private final PollsterRepository pollsters;
    private final HistoricalScoreService scoring;
    public PollsterPerformanceService(PollsterPerformanceRepository repository, PollsterRepository pollsters, HistoricalScoreService scoring) {
        this.repository = repository; this.pollsters = pollsters; this.scoring = scoring;
    }
    public List<PollsterPerformanceResponse> list() { return repository.findAllByOrderByElectionKeyDescRoundAscCreatedAtDesc().stream().map(this::response).toList(); }
    @Transactional public PollsterPerformanceResponse create(PollsterPerformanceRequest request) { return save(new PollsterPerformance(), request); }
    @Transactional public PollsterPerformanceResponse update(UUID id, PollsterPerformanceRequest request) {
        return save(repository.findById(id).orElseThrow(() -> new NotFoundException("Desempenho histórico não encontrado.")), request);
    }
    private PollsterPerformanceResponse save(PollsterPerformance p, PollsterPerformanceRequest r) {
        p.setPollster(pollsters.findById(r.pollsterId()).orElseThrow(() -> new NotFoundException("Instituto não encontrado.")));
        p.setElectionKey(r.electionKey().trim()); p.setRound(r.round()); p.setCandidateError(r.candidateError()); p.setSpreadError(r.spreadError());
        p.setHistoricalScore(scoring.factor(r.spreadError()).setScale(4, RoundingMode.HALF_UP));
        p.setSourceUrl(r.sourceUrl()); p.setSourceDescription(r.sourceDescription());
        return response(repository.saveAndFlush(p));
    }
    private PollsterPerformanceResponse response(PollsterPerformance p) {
        return new PollsterPerformanceResponse(p.getId(), PollsterResponse.from(p.getPollster()), p.getElectionKey(), p.getRound(),
            p.getCandidateError(), p.getSpreadError(), scoring.factor(p.getSpreadError()), p.getSourceUrl(), p.getSourceDescription(), p.getUpdatedAt());
    }
}
