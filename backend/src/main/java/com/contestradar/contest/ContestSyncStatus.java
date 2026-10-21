package com.contestradar.contest;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "contest_sync_status")
public class ContestSyncStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private Platform platform;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(nullable = false, length = 20)
    private String status = "UNKNOWN";

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "contests_count", nullable = false)
    private int contestsCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getContestsCount() { return contestsCount; }
    public void setContestsCount(int contestsCount) { this.contestsCount = contestsCount; }
    public Instant getUpdatedAt() { return updatedAt; }
}
