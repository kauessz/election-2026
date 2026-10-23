package br.com.election.poll;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PollResultRepository extends JpaRepository<PollResult, UUID> {
    @Query("select r from PollResult r join fetch r.candidate where r.poll.id = :pollId order by r.candidate.displayName")
    List<PollResult> findResults(UUID pollId);
    @Query("select r from PollResult r join fetch r.candidate where r.poll.id in :pollIds order by r.candidate.displayName, r.candidate.id")
    List<PollResult> findResultsForPolls(Collection<UUID> pollIds);
    @Modifying @Query("delete from PollResult r where r.poll.id = :pollId")
    void deleteResults(UUID pollId);
}
