package com.contestradar.contest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    Optional<Contest> findByPlatformAndExternalId(Platform platform, String externalId);

    @Query("""
        SELECT c FROM Contest c
        WHERE c.status IN :statuses
        AND (:platform IS NULL OR c.platform = :platform)
        AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY c.startTime ASC
    """)
    Page<Contest> findContests(
            @Param("statuses") List<ContestStatus> statuses,
            @Param("platform") Platform platform,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
        SELECT c FROM Contest c
        WHERE c.status IN :statuses
        AND (:platform IS NULL OR c.platform = :platform)
        AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
        AND c.startTime <= :maxTime
        ORDER BY c.startTime ASC
    """)
    Page<Contest> findContestsWithinWindow(
            @Param("statuses") List<ContestStatus> statuses,
            @Param("platform") Platform platform,
            @Param("search") String search,
            @Param("maxTime") Instant maxTime,
            Pageable pageable);

    List<Contest> findByStatusAndStartTimeBefore(ContestStatus status, Instant before);

    List<Contest> findByStatusInOrderByStartTimeAsc(List<ContestStatus> statuses);

    @Query("""
        SELECT c FROM Contest c
        WHERE c.status IN :statuses
        AND c.startTime <= :maxTime
        ORDER BY c.startTime ASC
    """)
    List<Contest> findByStatusInAndStartTimeBeforeOrEqualOrderByStartTimeAsc(
            @Param("statuses") List<ContestStatus> statuses,
            @Param("maxTime") Instant maxTime);
}
