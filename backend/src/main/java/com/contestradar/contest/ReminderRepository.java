package com.contestradar.contest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByUserId(Long userId);

    Optional<Reminder> findByUserIdAndContestId(Long userId, Long contestId);

    boolean existsByUserIdAndContestId(Long userId, Long contestId);

    @Modifying
    @Transactional
    void deleteByUserIdAndContestId(Long userId, Long contestId);

    List<Reminder> findByUserIdAndContestIdIn(Long userId, List<Long> contestIds);
}
