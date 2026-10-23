package br.com.election.tse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ElectionResultSnapshotRepository extends JpaRepository<ElectionResultSnapshot,UUID> {}
