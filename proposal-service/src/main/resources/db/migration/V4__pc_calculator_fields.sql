ALTER TABLE kp_proposals ADD COLUMN kit_qty INTEGER NOT NULL DEFAULT 1;
ALTER TABLE kp_proposals ADD COLUMN unit_kit_price NUMERIC(12,2);

UPDATE kp_proposals
SET unit_kit_price = ROUND(grand_total / GREATEST(kit_qty, 1), 2)
WHERE unit_kit_price IS NULL;

UPDATE kp_drone_models SET default_price = 2459000, name = 'HD540', updated_at = NOW() WHERE code = 'HD540';
UPDATE kp_drone_models SET default_price = 3259000, name = 'HD580', updated_at = NOW() WHERE code = 'HD580';
UPDATE kp_drone_models SET default_price = 1800000, name = 'HD525', updated_at = NOW() WHERE code = 'HD525';
UPDATE kp_drone_models SET default_price = 3485000, name = 'DJI T50', updated_at = NOW() WHERE code = 'T50';
UPDATE kp_drone_models SET default_price = 2200000, name = 'DJI T30', updated_at = NOW() WHERE code = 'T30';
UPDATE kp_drone_models SET active = FALSE, updated_at = NOW() WHERE code = 'T40';

INSERT INTO kp_drone_models(id, code, name, default_price, sort_order, active, created_at, updated_at)
VALUES
('77777777-7777-7777-7777-777777777777', 'T10', 'DJI T10', 1400000, 35, TRUE, NOW(), NOW()),
('88888888-8888-8888-8888-888888888888', 'M3M', 'DJI M3M', 800000, 45, TRUE, NOW(), NOW()),
('99999999-9999-9999-9999-999999999991', 'TRAILER_BAS', 'Прицеп БАС', 1900000, 50, TRUE, NOW(), NOW()),
('99999999-9999-9999-9999-999999999992', 'TRAILER_TENT', 'Прицеп ТЕНТ', 1500000, 55, TRUE, NOW(), NOW()),
('99999999-9999-9999-9999-999999999993', 'TRAILER_1', 'Прицеп 1 дрон', 1400000, 60, TRUE, NOW(), NOW()),
('99999999-9999-9999-9999-999999999994', 'MIXER_1000', 'Растворник на 1000л', 200000, 70, TRUE, NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET
  name = EXCLUDED.name,
  default_price = EXCLUDED.default_price,
  sort_order = EXCLUDED.sort_order,
  active = TRUE,
  updated_at = NOW();
