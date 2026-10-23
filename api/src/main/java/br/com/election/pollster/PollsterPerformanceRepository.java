package br.com.election.pollster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PollsterPerformanceRepository extends JpaRepository<PollsterPerformance, UUID> {}
