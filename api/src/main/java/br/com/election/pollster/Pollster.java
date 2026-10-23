package br.com.election.pollster;
import jakarta.persistence.*;
import br.com.election.common.TimestampedEntity;
import br.com.election.pollster.Pollster;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name = "pollsters")
public class Pollster extends TimestampedEntity {
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, unique = true, length = 200)
    private String slug;
    @Column(length = 2000)
    private String websiteUrl;
    @Column(nullable = false)
    private boolean active;
    public Pollster() {}
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getSlug() { return slug; }
    public void setSlug(String value) { this.slug = value; }
    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String value) { this.websiteUrl = value; }
    public boolean getActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
}
