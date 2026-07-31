CREATE TABLE kp_zip_items (
    id UUID PRIMARY KEY,
    drone_model_id UUID NOT NULL REFERENCES kp_drone_models(id) ON DELETE CASCADE,
    name VARCHAR(240) NOT NULL,
    sku VARCHAR(160),
    qty INTEGER NOT NULL DEFAULT 1,
    unit_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_kp_zip_items_model ON kp_zip_items(drone_model_id, sort_order);

ALTER TABLE kp_drone_models
    ADD COLUMN zip_name VARCHAR(240) NOT NULL DEFAULT 'ЗИП-пакет',
    ADD COLUMN zip_price NUMERIC(12,2);
