-- Полный прайс КП в БД: комплектующие (АКБ, зарядки…) редактируются в админке.
ALTER TABLE kp_drone_models
    ADD COLUMN IF NOT EXISTS price_components JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Сиды из price_list.json
UPDATE kp_drone_models SET price_components = '[
  {"name":"АККУМУЛЯТОР ЛИТИЙ-ИОННЫЙ ZAB1830","unitPrice":215000,"qtyPerKit":3},
  {"name":"УСТРОЙСТВО ЗАРЯДНОЕ HE202 ДЛЯ АКБ ДРОНА","unitPrice":205000,"qtyPerKit":1}
]'::jsonb, updated_at = NOW() WHERE code = 'HD540';

UPDATE kp_drone_models SET price_components = '[
  {"name":"АККУМУЛЯТОР ЛИТИЙ-ИОННЫЙ HE102 (30Ah)","unitPrice":230000,"qtyPerKit":3},
  {"name":"УСТРОЙСТВО ЗАРЯДНОЕ HE202 ДЛЯ АКБ ДРОНА","unitPrice":205000,"qtyPerKit":1}
]'::jsonb, updated_at = NOW() WHERE code = 'HD580';

UPDATE kp_drone_models SET price_components = '[
  {"name":"АККУМУЛЯТОР ЛИТИЙ-ИОННЫЙ HE102 (30Ah)","unitPrice":230000,"qtyPerKit":3},
  {"name":"УСТРОЙСТВО ЗАРЯДНОЕ HE202 ДЛЯ АКБ ДРОНА","unitPrice":205000,"qtyPerKit":1}
]'::jsonb, updated_at = NOW() WHERE code = 'HD525';

UPDATE kp_drone_models SET price_components = '[
  {"name":"ИНТЕЛЛЕКТУАЛЬНАЯ БАТАРЕЯ DB1560","unitPrice":255000,"qtyPerKit":3},
  {"name":"ГЕНЕРАТОР DJI D12500iE","unitPrice":494500,"qtyPerKit":1},
  {"name":"ЗАРЯДНАЯ СТАНЦИЯ ДЛЯ WB37","unitPrice":16500,"qtyPerKit":1},
  {"name":"WB37 INTELLIGENT BATTERY","unitPrice":14500,"qtyPerKit":2}
]'::jsonb, updated_at = NOW() WHERE code IN ('T50', 'DJI T50');

UPDATE kp_drone_models SET price_components = '[
  {"name":"ИНТЕЛЛЕКТУАЛЬНАЯ БАТАРЕЯ ДРОНА","unitPrice":205000,"qtyPerKit":3},
  {"name":"ЗАРЯДНАЯ СТАНЦИЯ ДЛЯ АКБ ДРОНА","unitPrice":220000,"qtyPerKit":1},
  {"name":"WB37 INTELLIGENT BATTERY","unitPrice":18000,"qtyPerKit":1}
]'::jsonb, updated_at = NOW() WHERE code IN ('T30', 'DJI T30');

UPDATE kp_drone_models SET price_components = '[
  {"name":"ИНТЕЛЛЕКТУАЛЬНАЯ БАТАРЕЯ ДРОНА","unitPrice":130000,"qtyPerKit":3},
  {"name":"ЗАРЯДНАЯ СТАНЦИЯ ДЛЯ АКБ ДРОНА","unitPrice":120000,"qtyPerKit":1},
  {"name":"WB37 INTELLIGENT BATTERY","unitPrice":18000,"qtyPerKit":1}
]'::jsonb, updated_at = NOW() WHERE code IN ('T10', 'DJI T10');

UPDATE kp_drone_models SET price_components = '[
  {"name":"ЗАРЯДНАЯ СТАНЦИЯ","unitPrice":25000,"qtyPerKit":1},
  {"name":"АКБ / WB37","unitPrice":38500,"qtyPerKit":2}
]'::jsonb, updated_at = NOW() WHERE code IN ('M3M', 'DJI M3M');

-- Прицепы / растворник — без комплектующих
UPDATE kp_drone_models SET price_components = '[]'::jsonb, updated_at = NOW()
WHERE code IN ('TRAILER_BAS', 'TRAILER_TENT', 'TRAILER_1', 'MIXER_1000');
