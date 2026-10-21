package com.contestradar.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {
    List<RatingHistory> findByProfileIdOrderByContestDateAsc(Long profileId);

    List<RatingHistory> findByProfileIdAndContestDateAfter(Long profileId, Instant after);

    @Query("SELECT rh FROM RatingHistory rh WHERE rh.profileId IN :profileIds AND rh.contestDate >= :startDate ORDER BY rh.contestDate ASC")
    List<RatingHistory> findByProfileIdsAndDateRange(List<Long> profileIds, Instant startDate);

    @Modifying
    @Transactional
    void deleteByProfileId(Long profileId);

    long countByProfileId(Long profileId);
}
