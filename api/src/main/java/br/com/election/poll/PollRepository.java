package br.com.election.poll;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PollRepository extends JpaRepository<Poll, UUID> {
    @EntityGraph(attributePaths = "pollster")
    List<Poll> findAllByOrderByPublishedAtDescFieldEndDescCreatedAtDescIdDesc();
    @Query(value = """
        SELECT latest.* FROM (
            SELECT DISTINCT ON (pollster_id) * FROM polls
            ORDER BY pollster_id, published_at DESC, field_end DESC, created_at DESC, id DESC
        ) latest ORDER BY published_at DESC, field_end DESC, created_at DESC, id DESC
        """, nativeQuery = true)
    List<Poll> findLatestByPollster();
}
