package br.com.election.common;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@MappedSuperclass
public abstract class TimestampedEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;
    @Column(nullable = false, updatable = false) protected Instant createdAt;
    @Column(nullable = false) protected Instant updatedAt;
    @PrePersist protected void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { updatedAt = Instant.now(); }
}
