ALTER TABLE articles DROP COLUMN IF EXISTS cover_object_name;
ALTER TABLE articles ADD COLUMN IF NOT EXISTS cover_media_id UUID;

CREATE TABLE IF NOT EXISTS media_files (
    id UUID PRIMARY KEY,
    original_name VARCHAR(400) NOT NULL,
    stored_name VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    uploaded_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_media_files_kind ON media_files(kind);
CREATE INDEX IF NOT EXISTS idx_media_files_created_at ON media_files(created_at DESC);
