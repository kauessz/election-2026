package br.com.election.poll;
import br.com.election.candidate.Candidate;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name = "poll_results", uniqueConstraints = @UniqueConstraint(columnNames = {"poll_id", "candidate_id"}))
public class PollResult {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "poll_id") private Poll poll;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "candidate_id") private Candidate candidate;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal originalPercentage;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal normalizedPercentage;
    protected PollResult() {}
    public PollResult(Poll poll, Candidate candidate, BigDecimal original, BigDecimal normalized) {
        this.poll = poll; this.candidate = candidate; originalPercentage = original; normalizedPercentage = normalized;
    }
    public Candidate getCandidate() { return candidate; }
    public UUID getPollId() { return poll.getId(); }
    public BigDecimal getOriginalPercentage() { return originalPercentage; }
    public BigDecimal getNormalizedPercentage() { return normalizedPercentage; }
}
