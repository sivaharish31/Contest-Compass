-- Profile verification support
ALTER TABLE connected_profiles
    ADD COLUMN verification_token VARCHAR(100) AFTER fetched_at,
    ADD COLUMN verification_method VARCHAR(30) AFTER verification_token,
    ADD COLUMN verification_expires_at DATETIME AFTER verification_method,
    ADD COLUMN verification_attempts INT NOT NULL DEFAULT 0 AFTER verification_expires_at,
    ADD COLUMN verified_at DATETIME AFTER verification_attempts,
    ADD COLUMN max_rating INT AFTER rating,
    ADD COLUMN rank_title VARCHAR(50) AFTER max_rating,
    ADD COLUMN problems_solved INT AFTER rank_title;

-- Prevent the same external account from being claimed by multiple users
CREATE UNIQUE INDEX uk_profiles_platform_handle ON connected_profiles(platform, handle);

-- Rating history for performance tracking
CREATE TABLE rating_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id      BIGINT NOT NULL,
    contest_name    VARCHAR(500),
    contest_date    DATETIME NOT NULL,
    rating_before   INT,
    rating_after    INT NOT NULL,
    rank_in_contest INT,
    performance     INT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rating_history_profile FOREIGN KEY (profile_id) REFERENCES connected_profiles(id) ON DELETE CASCADE,
    INDEX idx_rating_history_profile (profile_id),
    INDEX idx_rating_history_date (profile_id, contest_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
