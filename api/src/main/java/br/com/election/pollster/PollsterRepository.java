package br.com.election.pollster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PollsterRepository extends JpaRepository<Pollster, UUID> {}
