-- Прайс КП (start/drone) живёт в БД вместе с моделями; админка правит → калькулятор читает отсюда.
ALTER TABLE kp_drone_models
    ADD COLUMN IF NOT EXISTS start_price NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS drone_price NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS vat_mode VARCHAR(20) NOT NULL DEFAULT 'mixed';

UPDATE kp_drone_models
SET start_price = default_price
WHERE start_price IS NULL;

-- Сиды из price_list.json (комплектующие по-прежнему в JSON; здесь — цены комплекта и дрона).
UPDATE kp_drone_models SET start_price = 2459000, drone_price = 1609000, vat_mode = 'mixed', default_price = 2459000, updated_at = NOW() WHERE code = 'HD540';
UPDATE kp_drone_models SET start_price = 3259000, drone_price = 2364000, vat_mode = 'mixed', default_price = 3259000, updated_at = NOW() WHERE code = 'HD580';
UPDATE kp_drone_models SET start_price = 1800000, drone_price = 905000, vat_mode = 'mixed', default_price = 1800000, updated_at = NOW() WHERE code = 'HD525';
UPDATE kp_drone_models SET start_price = 3485000, drone_price = 2180000, vat_mode = 'mixed', default_price = 3485000, updated_at = NOW() WHERE code IN ('T50', 'DJI T50');
UPDATE kp_drone_models SET start_price = 2200000, drone_price = 1347000, vat_mode = 'all_vat', default_price = 2200000, updated_at = NOW() WHERE code IN ('T30', 'DJI T30');
UPDATE kp_drone_models SET start_price = 1400000, drone_price = 872000, vat_mode = 'mixed', default_price = 1400000, updated_at = NOW() WHERE code IN ('T10', 'DJI T10');
UPDATE kp_drone_models SET start_price = 800000, drone_price = 698000, vat_mode = 'all_vat', default_price = 800000, updated_at = NOW() WHERE code IN ('M3M', 'DJI M3M');
UPDATE kp_drone_models SET start_price = 1900000, drone_price = 1900000, vat_mode = 'all_vat', default_price = 1900000, updated_at = NOW() WHERE code = 'TRAILER_BAS';
UPDATE kp_drone_models SET start_price = 1500000, drone_price = 1500000, vat_mode = 'all_vat', default_price = 1500000, updated_at = NOW() WHERE code = 'TRAILER_TENT';
UPDATE kp_drone_models SET start_price = 1400000, drone_price = 1400000, vat_mode = 'all_vat', default_price = 1400000, updated_at = NOW() WHERE code = 'TRAILER_1';
UPDATE kp_drone_models SET start_price = 200000, drone_price = 200000, vat_mode = 'all_vat', default_price = 200000, updated_at = NOW() WHERE code = 'MIXER_1000';

-- Модели без явного сида: дрон = комплект (как прицеп).
UPDATE kp_drone_models
SET drone_price = COALESCE(start_price, default_price)
WHERE drone_price IS NULL;

UPDATE kp_drone_models
SET start_price = default_price
WHERE start_price IS NULL;

ALTER TABLE kp_drone_models
    ALTER COLUMN start_price SET NOT NULL,
    ALTER COLUMN drone_price SET NOT NULL;
