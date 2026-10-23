package br.com.election.pollster;
import jakarta.persistence.*;
import br.com.election.common.TimestampedEntity;
import br.com.election.pollster.Pollster;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name = "pollster_performance")
public class PollsterPerformance extends TimestampedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pollster_id")
    private Pollster pollster;
    @Column(nullable = false, length = 100)
    private String electionKey;
    @Column(nullable = false)
    private Integer round;
    @Column(precision = 12, scale = 4)
    private BigDecimal candidateError;
    @Column(precision = 12, scale = 4)
    private BigDecimal spreadError;
    @Column(precision = 12, scale = 4)
    private BigDecimal historicalScore;
    @Column(length = 2000)
    private String sourceUrl;
    @Column(length = 2000)
    private String sourceDescription;
    public PollsterPerformance() {}
    public Pollster getPollster() { return pollster; }
    public void setPollster(Pollster value) { this.pollster = value; }
    public String getElectionKey() { return electionKey; }
    public void setElectionKey(String value) { this.electionKey = value; }
    public Integer getRound() { return round; }
    public void setRound(Integer value) { this.round = value; }
    public BigDecimal getCandidateError() { return candidateError; }
    public void setCandidateError(BigDecimal value) { this.candidateError = value; }
    public BigDecimal getSpreadError() { return spreadError; }
    public void setSpreadError(BigDecimal value) { this.spreadError = value; }
    public BigDecimal getHistoricalScore() { return historicalScore; }
    public void setHistoricalScore(BigDecimal value) { this.historicalScore = value; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) { sourceUrl = value; }
    public String getSourceDescription() { return sourceDescription; }
    public void setSourceDescription(String value) { sourceDescription = value; }
}
