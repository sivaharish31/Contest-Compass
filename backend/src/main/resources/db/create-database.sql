-- Run this script as a MySQL user with CREATE/GRANT privileges.
-- Before running, set CONTESTRADAR_DB_PASSWORD to a strong password of your choice:
--
--   On Linux/macOS:
--     export CONTESTRADAR_DB_PASSWORD='your-strong-password-here'
--     envsubst < create-database.sql | mysql -u root -p
--
--   Or edit this file locally (do NOT commit the real password):
--     Replace CHANGE_ME below, run the script, then revert the file.
--
-- The same password must be supplied to the application at runtime via the
-- DATABASE_PASSWORD environment variable (see .env.example).

CREATE DATABASE IF NOT EXISTS contestradar
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'contestradar'@'localhost' IDENTIFIED BY 'CHANGE_ME';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
    ON contestradar.* TO 'contestradar'@'localhost';

FLUSH PRIVILEGES;
