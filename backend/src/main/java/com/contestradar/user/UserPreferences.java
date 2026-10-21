package com.contestradar.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "email_notifications", nullable = false)
    private Boolean emailNotifications = true;

    @Column(name = "browser_notifications", nullable = false)
    private Boolean browserNotifications = false;

    @Column(name = "preferred_platforms", length = 200)
    private String preferredPlatforms = "CODEFORCES,LEETCODE,CODECHEF,ATCODER";

    @Column(name = "preferred_contest_times", length = 500)
    private String preferredContestTimes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserPreferences() {}

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Boolean getEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(Boolean emailNotifications) { this.emailNotifications = emailNotifications; }

    public Boolean getBrowserNotifications() { return browserNotifications; }
    public void setBrowserNotifications(Boolean browserNotifications) { this.browserNotifications = browserNotifications; }

    public String getPreferredPlatforms() { return preferredPlatforms; }
    public void setPreferredPlatforms(String preferredPlatforms) { this.preferredPlatforms = preferredPlatforms; }

    public String getPreferredContestTimes() { return preferredContestTimes; }
    public void setPreferredContestTimes(String preferredContestTimes) { this.preferredContestTimes = preferredContestTimes; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
