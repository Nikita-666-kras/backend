ALTER TABLE media_files
    ADD COLUMN IF NOT EXISTS section VARCHAR(20) NOT NULL DEFAULT 'OTHER';

CREATE INDEX IF NOT EXISTS idx_media_files_section ON media_files(section);
