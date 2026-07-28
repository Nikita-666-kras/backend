CREATE TABLE part_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL UNIQUE,
    parent_id UUID REFERENCES part_categories(id) ON DELETE SET NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_part_categories_parent ON part_categories(parent_id);

CREATE TABLE drones (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    description VARCHAR(1000),
    image_media_id UUID,
    status VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_drones_status ON drones(status);

CREATE TABLE parts (
    id UUID PRIMARY KEY,
    name VARCHAR(240) NOT NULL,
    sku VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'RUB',
    drone_id UUID REFERENCES drones(id) ON DELETE SET NULL,
    category_id UUID REFERENCES part_categories(id) ON DELETE SET NULL,
    cover_media_id UUID,
    status VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    external_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    external_id VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_parts_status ON parts(status);
CREATE INDEX idx_parts_drone ON parts(drone_id);
CREATE INDEX idx_parts_category ON parts(category_id);
CREATE INDEX idx_parts_external ON parts(external_source, external_id);

CREATE TABLE part_media (
    part_id UUID NOT NULL REFERENCES parts(id) ON DELETE CASCADE,
    media_id UUID NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (part_id, media_id)
);

CREATE TABLE kits (
    id UUID PRIMARY KEY,
    name VARCHAR(240) NOT NULL,
    sku VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'RUB',
    price_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    drone_id UUID REFERENCES drones(id) ON DELETE SET NULL,
    cover_media_id UUID,
    status VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_kits_status ON kits(status);
CREATE INDEX idx_kits_drone ON kits(drone_id);

CREATE TABLE kit_items (
    kit_id UUID NOT NULL REFERENCES kits(id) ON DELETE CASCADE,
    part_id UUID NOT NULL REFERENCES parts(id) ON DELETE RESTRICT,
    qty INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (kit_id, part_id)
);

CREATE TABLE kit_media (
    kit_id UUID NOT NULL REFERENCES kits(id) ON DELETE CASCADE,
    media_id UUID NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (kit_id, media_id)
);
