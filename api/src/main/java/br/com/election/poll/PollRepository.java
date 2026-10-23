package br.com.election.poll;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PollRepository extends JpaRepository<Poll, UUID> {
    @EntityGraph(attributePaths = "pollster")
    List<Poll> findAllByOrderByPublishedAtDescFieldEndDescCreatedAtDescIdDesc();
    @Query(value = """
        SELECT latest.* FROM (
            SELECT DISTINCT ON (pollster_id) * FROM polls
            WHERE (CAST(:publishedBefore AS timestamptz) IS NULL OR published_at < :publishedBefore)
            ORDER BY pollster_id, published_at DESC, field_end DESC, created_at DESC, id DESC
        ) latest ORDER BY published_at DESC, field_end DESC, created_at DESC, id DESC
        """, nativeQuery = true)
    List<Poll> findLatestByPollsterBefore(java.time.Instant publishedBefore);

    default List<Poll> findLatestByPollster() { return findLatestByPollsterBefore(null); }

    @EntityGraph(attributePaths = "pollster")
    List<Poll> findByIdIn(Collection<UUID> ids);
}
