package br.com.election.poll;
import jakarta.persistence.*;
import br.com.election.common.TimestampedEntity;
import br.com.election.pollster.Pollster;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name = "polls")
public class Poll extends TimestampedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pollster_id")
    private Pollster pollster;
    @Column(length = 100)
    private String tseRegistrationNumber;
    @Column(nullable = false)
    private LocalDate fieldStart;
    @Column(nullable = false)
    private LocalDate fieldEnd;
    @Column(nullable = false)
    private Instant publishedAt;
    
    private Integer sampleSize;
    @Column(precision = 7, scale = 4)
    private BigDecimal marginOfError;
    @Column(length = 5000)
    private String methodology;
    @Column(nullable = false, length = 2000)
    private String sourceUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private VoteBasis voteBasis;
    public Poll() {}
    public Pollster getPollster() { return pollster; }
    public void setPollster(Pollster value) { this.pollster = value; }
    public String getTseRegistrationNumber() { return tseRegistrationNumber; }
    public void setTseRegistrationNumber(String value) { this.tseRegistrationNumber = value; }
    public LocalDate getFieldStart() { return fieldStart; }
    public void setFieldStart(LocalDate value) { this.fieldStart = value; }
    public LocalDate getFieldEnd() { return fieldEnd; }
    public void setFieldEnd(LocalDate value) { this.fieldEnd = value; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant value) { this.publishedAt = value; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer value) { this.sampleSize = value; }
    public BigDecimal getMarginOfError() { return marginOfError; }
    public void setMarginOfError(BigDecimal value) { this.marginOfError = value; }
    public String getMethodology() { return methodology; }
    public void setMethodology(String value) { this.methodology = value; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) { this.sourceUrl = value; }
    public VoteBasis getVoteBasis() { return voteBasis; }
    public void setVoteBasis(VoteBasis value) { this.voteBasis = value; }
}
