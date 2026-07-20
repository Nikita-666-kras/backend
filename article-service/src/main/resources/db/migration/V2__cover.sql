CREATE TABLE media_files (
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

CREATE INDEX idx_media_files_kind ON media_files(kind);
CREATE INDEX idx_media_files_created_at ON media_files(created_at DESC);

ALTER TABLE articles ADD COLUMN IF NOT EXISTS cover_media_id UUID REFERENCES media_files(id) ON DELETE SET NULL;

ALTER TABLE articles DROP COLUMN IF EXISTS cover_object_name;
