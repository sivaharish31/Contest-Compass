package com.contestradar.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rating_history")
public class RatingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "contest_name", length = 500)
    private String contestName;

    @Column(name = "contest_date", nullable = false)
    private Instant contestDate;

    @Column(name = "rating_before")
    private Integer ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private Integer ratingAfter;

    @Column(name = "rank_in_contest")
    private Integer rankInContest;

    @Column
    private Integer performance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RatingHistory() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public String getContestName() {
        return contestName;
    }

    public void setContestName(String contestName) {
        this.contestName = contestName;
    }

    public Instant getContestDate() {
        return contestDate;
    }

    public void setContestDate(Instant contestDate) {
        this.contestDate = contestDate;
    }

    public Integer getRatingBefore() {
        return ratingBefore;
    }

    public void setRatingBefore(Integer ratingBefore) {
        this.ratingBefore = ratingBefore;
    }

    public Integer getRatingAfter() {
        return ratingAfter;
    }

    public void setRatingAfter(Integer ratingAfter) {
        this.ratingAfter = ratingAfter;
    }

    public Integer getRankInContest() {
        return rankInContest;
    }

    public void setRankInContest(Integer rankInContest) {
        this.rankInContest = rankInContest;
    }

    public Integer getPerformance() {
        return performance;
    }

    public void setPerformance(Integer performance) {
        this.performance = performance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
