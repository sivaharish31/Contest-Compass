package com.contestradar.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectedProfileRepository extends JpaRepository<ConnectedProfile, Long> {

    List<ConnectedProfile> findByUserId(Long userId);

    Optional<ConnectedProfile> findByUserIdAndPlatform(Long userId, String platform);

    Optional<ConnectedProfile> findByPlatformAndHandle(String platform, String handle);

    @Modifying
    @Transactional
    void deleteByIdAndUserId(Long id, Long userId);
}
