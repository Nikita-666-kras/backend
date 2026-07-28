CREATE TABLE kp_drone_models (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    default_price NUMERIC(12,2) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE kp_settings (
    id INTEGER PRIMARY KEY,
    last_kp_number INTEGER NOT NULL DEFAULT 1,
    company_name VARCHAR(240) NOT NULL DEFAULT 'АТРИС',
    file_suffix VARCHAR(240) NOT NULL DEFAULT 'КП от АТРИС'
);
INSERT INTO kp_settings(id, last_kp_number) VALUES (1, 661);

CREATE TABLE kp_proposals (
    id UUID PRIMARY KEY,
    number INTEGER NOT NULL,
    manager_id UUID NOT NULL,
    manager_username VARCHAR(120) NOT NULL,
    recipient VARCHAR(240) NOT NULL,
    drone_model_id UUID NOT NULL REFERENCES kp_drone_models(id),
    drone_model_name VARCHAR(120) NOT NULL,
    drone_price NUMERIC(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL,
    discount_total NUMERIC(12,2) NOT NULL,
    grand_total NUMERIC(12,2) NOT NULL,
    nds_total NUMERIC(12,2) NOT NULL,
    pdf_path VARCHAR(400),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_kp_proposals_manager ON kp_proposals(manager_id, updated_at DESC);

CREATE TABLE kp_proposal_lines (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES kp_proposals(id) ON DELETE CASCADE,
    line_type VARCHAR(20) NOT NULL,
    ref_id UUID,
    sku VARCHAR(160),
    name VARCHAR(240) NOT NULL,
    qty INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    discount_pct INTEGER NOT NULL DEFAULT 0,
    line_total NUMERIC(12,2) NOT NULL
);

INSERT INTO kp_drone_models(id, code, name, default_price, sort_order, active, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'HD580', 'HD580', 3250000, 10, TRUE, NOW(), NOW()),
('22222222-2222-2222-2222-222222222222', 'HD525', 'HD525', 1800000, 20, TRUE, NOW(), NOW()),
('33333333-3333-3333-3333-333333333333', 'T50', 'T50', 3450000, 30, TRUE, NOW(), NOW()),
('44444444-4444-4444-4444-444444444444', 'T40', 'T40', 3300000, 40, TRUE, NOW(), NOW()),
('55555555-5555-5555-5555-555555555555', 'T30', 'T30', 1800000, 50, TRUE, NOW(), NOW());
