package br.com.election.candidate;
import jakarta.persistence.*;
import br.com.election.common.TimestampedEntity;
import br.com.election.pollster.Pollster;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name = "candidates")
public class Candidate extends TimestampedEntity {
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 200)
    private String displayName;
    @Column(nullable = false, length = 50)
    private String party;
    @Column(nullable = false)
    private Integer ballotNumber;
    @Column(nullable = false)
    private boolean active;
    public Candidate() {}
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { this.displayName = value; }
    public String getParty() { return party; }
    public void setParty(String value) { this.party = value; }
    public Integer getBallotNumber() { return ballotNumber; }
    public void setBallotNumber(Integer value) { this.ballotNumber = value; }
    public boolean getActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
}
