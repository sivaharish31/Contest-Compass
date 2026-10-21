-- Add difficulty breakdown and submission calendar to connected_profiles
ALTER TABLE connected_profiles
ADD COLUMN easy_solved INT DEFAULT NULL,
ADD COLUMN medium_solved INT DEFAULT NULL,
ADD COLUMN hard_solved INT DEFAULT NULL,
ADD COLUMN submission_calendar MEDIUMTEXT DEFAULT NULL;
