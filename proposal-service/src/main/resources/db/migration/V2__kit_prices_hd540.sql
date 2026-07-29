-- Update drone prices from production KP templates; add HD540
UPDATE kp_drone_models SET default_price = 2305000, name = 'HD580', updated_at = NOW() WHERE code = 'HD580';
UPDATE kp_drone_models SET default_price = 1105000, name = 'HD525', updated_at = NOW() WHERE code = 'HD525';
UPDATE kp_drone_models SET default_price = 2180000, name = 'T50', updated_at = NOW() WHERE code = 'T50';
UPDATE kp_drone_models SET default_price = 1795000, name = 'T40', updated_at = NOW() WHERE code = 'T40';
UPDATE kp_drone_models SET default_price = 1147000, name = 'T30', updated_at = NOW() WHERE code = 'T30';

INSERT INTO kp_drone_models(id, code, name, default_price, sort_order, active, created_at, updated_at)
VALUES ('66666666-6666-6666-6666-666666666666', 'HD540', 'HD540', 1550000, 15, TRUE, NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET
  default_price = EXCLUDED.default_price,
  name = EXCLUDED.name,
  sort_order = EXCLUDED.sort_order,
  active = TRUE,
  updated_at = NOW();
