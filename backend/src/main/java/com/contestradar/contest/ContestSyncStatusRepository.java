package com.contestradar.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContestSyncStatusRepository extends JpaRepository<ContestSyncStatus, Long> {
    Optional<ContestSyncStatus> findByPlatform(Platform platform);
    List<ContestSyncStatus> findAllByOrderByPlatformAsc();
}
