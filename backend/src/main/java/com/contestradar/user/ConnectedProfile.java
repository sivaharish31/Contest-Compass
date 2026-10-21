package com.contestradar.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "connected_profiles", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "platform"}))
public class ConnectedProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String platform;

    @Column(nullable = false, length = 100)
    private String handle;

    @Column(nullable = false)
    private Boolean verified = false;

    @Column
    private Integer rating;

    @Column(name = "profile_url", length = 500)
    private String profileUrl;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "verification_token", length = 100)
    private String verificationToken;

    @Column(name = "verification_method", length = 30)
    private String verificationMethod;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

    @Column(name = "verification_attempts", nullable = false)
    private Integer verificationAttempts = 0;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "max_rating")
    private Integer maxRating;

    @Column(name = "rank_title", length = 50)
    private String rankTitle;

    @Column(name = "problems_solved")
    private Integer problemsSolved;

    @Column(name = "easy_solved")
    private Integer easySolved;

    @Column(name = "medium_solved")
    private Integer mediumSolved;

    @Column(name = "hard_solved")
    private Integer hardSolved;

    @Column(name = "submission_calendar", columnDefinition = "MEDIUMTEXT")
    private String submissionCalendar;

    @Column(name = "last_sync_status", length = 50)
    private String lastSyncStatus;

    @Column(name = "last_sync_error", columnDefinition = "TEXT")
    private String lastSyncError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ConnectedProfile() {}

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getHandle() { return handle; }
    public void setHandle(String handle) { this.handle = handle; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getProfileUrl() { return profileUrl; }
    public void setProfileUrl(String profileUrl) { this.profileUrl = profileUrl; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }

    public Instant getVerificationExpiresAt() { return verificationExpiresAt; }
    public void setVerificationExpiresAt(Instant verificationExpiresAt) { this.verificationExpiresAt = verificationExpiresAt; }

    public Integer getVerificationAttempts() { return verificationAttempts; }
    public void setVerificationAttempts(Integer verificationAttempts) { this.verificationAttempts = verificationAttempts; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public Integer getMaxRating() { return maxRating; }
    public void setMaxRating(Integer maxRating) { this.maxRating = maxRating; }

    public String getRankTitle() { return rankTitle; }
    public void setRankTitle(String rankTitle) { this.rankTitle = rankTitle; }

    public Integer getProblemsSolved() { return problemsSolved; }
    public void setProblemsSolved(Integer problemsSolved) { this.problemsSolved = problemsSolved; }

    public Integer getEasySolved() { return easySolved; }
    public void setEasySolved(Integer easySolved) { this.easySolved = easySolved; }

    public Integer getMediumSolved() { return mediumSolved; }
    public void setMediumSolved(Integer mediumSolved) { this.mediumSolved = mediumSolved; }

    public Integer getHardSolved() { return hardSolved; }
    public void setHardSolved(Integer hardSolved) { this.hardSolved = hardSolved; }

    public String getSubmissionCalendar() { return submissionCalendar; }
    public void setSubmissionCalendar(String submissionCalendar) { this.submissionCalendar = submissionCalendar; }

    public String getLastSyncStatus() { return lastSyncStatus; }
    public void setLastSyncStatus(String lastSyncStatus) { this.lastSyncStatus = lastSyncStatus; }

    public String getLastSyncError() { return lastSyncError; }
    public void setLastSyncError(String lastSyncError) { this.lastSyncError = lastSyncError; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
