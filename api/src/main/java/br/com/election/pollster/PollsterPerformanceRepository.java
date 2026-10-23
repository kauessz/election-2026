package br.com.election.pollster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.UUID;
public interface PollsterPerformanceRepository extends JpaRepository<PollsterPerformance, UUID> {
    @EntityGraph(attributePaths = "pollster")
    List<PollsterPerformance> findByElectionKeyAndRound(String electionKey, Integer round);
    @EntityGraph(attributePaths = "pollster")
    List<PollsterPerformance> findAllByOrderByElectionKeyDescRoundAscCreatedAtDesc();
}
