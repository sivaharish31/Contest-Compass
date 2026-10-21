package com.contestradar.contest;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "contests", uniqueConstraints = @UniqueConstraint(columnNames = {"platform", "external_id"}))
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Platform platform;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "registration_url", length = 1000)
    private String registrationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContestStatus status = ContestStatus.UPCOMING;

    @Column(name = "rating_min")
    private Integer ratingMin;

    @Column(name = "rating_max")
    private Integer ratingMax;

    @Column(name = "data_source", length = 100)
    private String dataSource;

    @Column(name = "source_confidence", length = 20)
    private String sourceConfidence = "HIGH";

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Contest() {}

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getRegistrationUrl() { return registrationUrl; }
    public void setRegistrationUrl(String registrationUrl) { this.registrationUrl = registrationUrl; }
    public ContestStatus getStatus() { return status; }
    public void setStatus(ContestStatus status) { this.status = status; }
    public Integer getRatingMin() { return ratingMin; }
    public void setRatingMin(Integer ratingMin) { this.ratingMin = ratingMin; }
    public Integer getRatingMax() { return ratingMax; }
    public void setRatingMax(Integer ratingMax) { this.ratingMax = ratingMax; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public String getSourceConfidence() { return sourceConfidence; }
    public void setSourceConfidence(String sourceConfidence) { this.sourceConfidence = sourceConfidence; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
