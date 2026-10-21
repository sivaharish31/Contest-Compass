CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    experience_level VARCHAR(20) DEFAULT 'INTERMEDIATE',
    timezone        VARCHAR(50) DEFAULT 'UTC',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE connected_profiles (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    platform    VARCHAR(30) NOT NULL,
    handle      VARCHAR(100) NOT NULL,
    verified    BOOLEAN NOT NULL DEFAULT FALSE,
    rating      INT,
    profile_url VARCHAR(500),
    fetched_at  DATETIME,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_connected_profiles_user_platform (user_id, platform),
    CONSTRAINT fk_connected_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE contests (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_id     VARCHAR(200) NOT NULL,
    platform        VARCHAR(30) NOT NULL,
    name            VARCHAR(500) NOT NULL,
    start_time      DATETIME NOT NULL,
    end_time        DATETIME,
    duration_seconds BIGINT,
    url             VARCHAR(1000) NOT NULL,
    registration_url VARCHAR(1000),
    status          VARCHAR(30) NOT NULL DEFAULT 'UPCOMING',
    rating_min      INT,
    rating_max      INT,
    data_source     VARCHAR(100),
    source_confidence VARCHAR(20) DEFAULT 'HIGH',
    last_synced_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_contests_platform_external_id (platform, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE saved_contests (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    contest_id  BIGINT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_saved_contests_user_contest (user_id, contest_id),
    CONSTRAINT fk_saved_contests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_contests_contest FOREIGN KEY (contest_id) REFERENCES contests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reminders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    contest_id      BIGINT NOT NULL,
    remind_at       DATETIME NOT NULL,
    lead_minutes    INT NOT NULL DEFAULT 10,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reminders_user_contest (user_id, contest_id),
    CONSTRAINT fk_reminders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reminders_contest FOREIGN KEY (contest_id) REFERENCES contests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_deliveries (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reminder_id     BIGINT NOT NULL,
    channel         VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempted_at    DATETIME,
    delivered_at    DATETIME,
    error_message   VARCHAR(1000),
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_deliveries_reminder FOREIGN KEY (reminder_id) REFERENCES reminders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_preferences (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                 BIGINT NOT NULL,
    email_notifications     BOOLEAN NOT NULL DEFAULT TRUE,
    browser_notifications   BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_platforms     VARCHAR(200) DEFAULT 'CODEFORCES,LEETCODE,CODECHEF,ATCODER',
    preferred_contest_times VARCHAR(500),
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_preferences_user (user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE contest_sync_status (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform        VARCHAR(30) NOT NULL,
    last_synced_at  DATETIME,
    status          VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    error_message   VARCHAR(1000),
    contests_count  INT NOT NULL DEFAULT 0,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_contest_sync_status_platform (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_contests_platform_status ON contests(platform, status);
CREATE INDEX idx_contests_start_time ON contests(start_time);
CREATE INDEX idx_reminders_status_remind_at ON reminders(status, remind_at);
CREATE INDEX idx_saved_contests_user ON saved_contests(user_id);
CREATE INDEX idx_notification_deliveries_status ON notification_deliveries(status);
