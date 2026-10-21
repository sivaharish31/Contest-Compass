-- Add sync status tracking fields to connected_profiles
-- These fields help track synchronization success/failure and provide better error reporting

ALTER TABLE connected_profiles
ADD COLUMN last_sync_status VARCHAR(50) DEFAULT NULL COMMENT 'Sync result: SUCCESS, PARTIAL, FAILED, or NULL if never synced';

ALTER TABLE connected_profiles
ADD COLUMN last_sync_error TEXT DEFAULT NULL COMMENT 'Detailed error message from last sync attempt';
