package com.blog.platform.proposal.service;

import com.blog.platform.proposal.api.dto.KpDtos.LineType;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalLineRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Логика как в PC-калькуляторе Agro-Tech:
 * менеджер двигает цену продажи — система режет/поднимает только дрон;
 * комплектующие фиксированы (из price_list.json);
 * start/drone/vat берутся из БД (админка), с fallback на JSON.
 */
@Service
@RequiredArgsConstructor
public class KpPcCalculatorService {

    private static final Set<String> META_KEYS = Set.of("config_version", "last_kp_number");

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    /** Базовый прайс из JSON (комплектующие + дефолтные цены). */
    private final Map<String, ModelPrice> jsonPrices = new LinkedHashMap<>();
    /** Актуальный прайс: JSON + оверлей из БД. */
    private volatile Map<String, ModelPrice> prices = Map.of();

    public record Component(String name, BigDecimal unitPrice, int qtyPerKit) {}

    public record ModelPrice(
            String key,
            String vatMode,
            BigDecimal startPrice,
            BigDecimal dronePrice,
            List<Component> components
    ) {}

    public record CalcResult(
            String priceKey,
            String vatMode,
            int kitQty,
            BigDecimal unitKitPrice,
            BigDecimal startPrice,
            BigDecimal priceDiff,
            BigDecimal unitDronePrice,
            BigDecimal baseDronePrice,
            BigDecimal droneTotal,
            BigDecimal grandTotal,
            BigDecimal ndsTotal,
            List<ProposalLineRequest> lines
    ) {}

    @PostConstruct
    void init() {
        loadJson();
        reloadFromDb();
    }

    /** Перечитать цены моделей из БД (после сохранения в админке). */
    public synchronized void reloadFromDb() {
        Map<String, ModelPrice> next = new LinkedHashMap<>(jsonPrices);
        jdbc.query("""
                select code, name, start_price, drone_price, vat_mode, price_components::text as price_components
                from kp_drone_models
                where active = true
                """, (rs) -> {
            String code = rs.getString("code");
            String name = rs.getString("name");
            BigDecimal start = rs.getBigDecimal("start_price").setScale(2, RoundingMode.HALF_UP);
            BigDecimal drone = rs.getBigDecimal("drone_price").setScale(2, RoundingMode.HALF_UP);
            String vat = rs.getString("vat_mode");
            if (vat == null || vat.isBlank()) {
                vat = "mixed";
            }
            String lookup = code + " " + name;
            String key = resolveKey(lookup);
            ModelPrice base = next.get(key);
            if (base == null) {
                base = next.get(code);
            }
            if (base == null) {
                base = next.get(lookup);
            }
            String rawComponents = rs.getString("price_components");
            List<Component> components;
            if (rawComponents == null) {
                components = base != null ? base.components() : List.of();
            } else {
                components = parseComponentsJson(rawComponents);
            }
            String priceKey = base != null ? base.key() : (name != null && !name.isBlank() ? name : code);
            ModelPrice overlay = new ModelPrice(priceKey, vat, start, drone, components);
            next.put(priceKey, overlay);
            if (!priceKey.equals(code)) {
                next.put(code, overlay);
            }
            next.put(lookup, overlay);
            if (name != null && !name.isBlank()) {
                next.put(name, overlay);
            }
        });
        prices = Map.copyOf(next);
    }

    private List<Component> parseComponentsJson(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray() || arr.isEmpty()) {
                return List.of();
            }
            List<Component> out = new ArrayList<>();
            for (JsonNode n : arr) {
                String name = text(n, "name", null);
                if (name == null || name.isBlank()) {
                    continue;
                }
                BigDecimal price = optionalMoney(n, "unitPrice");
                if (price == null) {
                    price = optionalMoney(n, "unit_price");
                }
                if (price == null) {
                    continue;
                }
                int qty = intOr(n, "qtyPerKit", intOr(n, "qty_per_kit", 1));
                if (qty < 1) {
                    continue;
                }
                out.add(new Component(name.trim(), price, qty));
            }
            return List.copyOf(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse price_components JSON", ex);
        }
    }

    private void loadJson() {
        try (InputStream in = new ClassPathResource("kp/price_list.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                if (META_KEYS.contains(e.getKey()) || !e.getValue().isObject()) {
                    continue;
                }
                JsonNode n = e.getValue();
                List<Component> components = new ArrayList<>();
                addComponent(components, n, "akb_price", "akb_name", "АКБ", "akb_qty_per_kit", 3);
                addComponent(components, n, "zaryad_price", "zaryad_name", "Зарядное устройство", "zaryad_qty_per_kit", 1);
                addComponent(components, n, "WB37_hub_price", "WB37_hub_name", "Зарядная станция WB37", "WB37_hub_qty_per_kit", 1);
                addComponent(components, n, "WB37_price", "WB37_name", "WB37", "WB37_qty_per_kit", 2);
                jsonPrices.put(e.getKey(), new ModelPrice(
                        e.getKey(),
                        text(n, "vat_mode", "mixed"),
                        money(n, "start_price"),
                        money(n, "drone_price"),
                        List.copyOf(components)
                ));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load kp/price_list.json", ex);
        }
    }

    public List<String> priceKeys() {
        return List.copyOf(prices.keySet());
    }

    public ModelPrice requirePrice(String modelCodeOrName) {
        ModelPrice price = findPrice(modelCodeOrName);
        if (price == null) {
            throw new IllegalArgumentException("Нет прайса калькулятора для модели: " + modelCodeOrName);
        }
        return price;
    }

    public ModelPrice findPrice(String modelCodeOrName) {
        if (modelCodeOrName == null || modelCodeOrName.isBlank()) {
            return null;
        }
        ModelPrice direct = prices.get(modelCodeOrName);
        if (direct != null) {
            return direct;
        }
        return prices.get(resolveKey(modelCodeOrName));
    }

    public CalcResult calculate(String modelCodeOrName, int kitQty, BigDecimal unitKitPrice) {
        return calculate(modelCodeOrName, kitQty, unitKitPrice, null);
    }

    /**
     * @param droneVatPct НДС на дрон: 0 или 22. null — взять из прайса модели.
     *                    Комплектующие всегда в базе НДС 22% (кроме случая, когда весь комплект all_vat).
     */
    public CalcResult calculate(String modelCodeOrName, int kitQty, BigDecimal unitKitPrice, Integer droneVatPct) {
        if (kitQty < 1) {
            throw new IllegalArgumentException("Количество комплектов должно быть целым числом >= 1");
        }
        if (unitKitPrice == null) {
            throw new IllegalArgumentException("Укажите цену одного комплекта числом");
        }
        if (unitKitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Цена комплекта должна быть >= 0");
        }

        ModelPrice data = requirePrice(modelCodeOrName);
        String vatMode = resolveVatMode(data.vatMode(), droneVatPct);
        BigDecimal target = unitKitPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = data.startPrice().subtract(target).setScale(2, RoundingMode.HALF_UP);

        BigDecimal unitDrone = data.dronePrice().subtract(diff).setScale(2, RoundingMode.HALF_UP);
        if (unitDrone.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Цена комплекта слишком низкая: после скидки цена дрона уходит в минус. "
                            + "Минимум ≈ " + moneyFmt(data.startPrice().subtract(data.dronePrice())) + " ₽");
        }

        List<ProposalLineRequest> lines = new ArrayList<>();
        for (Component c : data.components()) {
            int qty = Math.multiplyExact(c.qtyPerKit(), kitQty);
            lines.add(part(c.name(), qty, c.unitPrice()));
        }

        BigDecimal droneTotal = unitDrone.multiply(BigDecimal.valueOf(kitQty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grand = target.multiply(BigDecimal.valueOf(kitQty)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal ndsBase = "all_vat".equalsIgnoreCase(vatMode)
                ? grand
                : grand.subtract(droneTotal).max(BigDecimal.ZERO);
        BigDecimal nds = ndsBase.multiply(BigDecimal.valueOf(22))
                .divide(BigDecimal.valueOf(122), 2, RoundingMode.HALF_UP);

        return new CalcResult(
                data.key(),
                vatMode,
                kitQty,
                target,
                data.startPrice(),
                diff,
                unitDrone,
                data.dronePrice(),
                droneTotal,
                grand,
                nds,
                List.copyOf(lines)
        );
    }

    public static String resolveVatMode(String priceListMode, Integer droneVatPct) {
        if (droneVatPct != null) {
            if (droneVatPct == 0) return "mixed";
            if (droneVatPct == 22) return "all_vat";
            throw new IllegalArgumentException("НДС на дрон: допустимы только 0% или 22%");
        }
        return priceListMode == null || priceListMode.isBlank() ? "mixed" : priceListMode;
    }

    public static int vatPctFromMode(String vatMode) {
        return "all_vat".equalsIgnoreCase(vatMode) ? 22 : 0;
    }

    private void addComponent(
            List<Component> components,
            JsonNode n,
            String priceField,
            String nameField,
            String defaultName,
            String qtyField,
            int defaultQty
    ) {
        BigDecimal price = optionalMoney(n, priceField);
        if (price == null) {
            return;
        }
        int qty = intOr(n, qtyField, defaultQty);
        if (qty < 1) {
            throw new IllegalStateException(priceField + ": qty_per_kit must be >= 1");
        }
        components.add(new Component(text(n, nameField, defaultName), price, qty));
    }

    String resolveKey(String value) {
        String upper = value.toUpperCase(Locale.ROOT)
                .replace("VECTOR AGR ", "")
                .replace("DJI AGRAS ", "")
                .trim();
        if (upper.contains("HD580")) return "HD580";
        if (upper.contains("HD540")) return "HD540";
        if (upper.contains("HD525")) return "HD525";
        if (upper.contains("T50") || upper.contains("DJI T50")) return "DJI T50";
        if (upper.contains("T30") || upper.contains("DJI T30")) return "DJI T30";
        if (upper.contains("T10") || upper.contains("DJI T10")) return "DJI T10";
        if (upper.contains("M3M")) return "DJI M3M";
        if (upper.contains("MIXER") || upper.contains("РАСТВОРНИК") || upper.contains("1000Л") || upper.contains("1000L")) {
            return "Растворник на 1000л";
        }
        if (upper.contains("TRAILER_TENT") || upper.contains("ТЕНТ")) return "Прицеп ТЕНТ";
        if (upper.contains("TRAILER_1") || upper.contains("1 ДРОН") || upper.contains("1_ДРОН")) {
            return "Прицеп 1 дрон";
        }
        if (upper.contains("TRAILER_BAS") || (upper.contains("ПРИЦЕП") && upper.contains("БАС"))) {
            return "Прицеп БАС";
        }
        if (upper.equals("T50")) return "DJI T50";
        if (upper.equals("T30")) return "DJI T30";
        if (upper.equals("T10")) return "DJI T10";
        return value;
    }

    private static ProposalLineRequest part(String name, int qty, BigDecimal unitPrice) {
        return new ProposalLineRequest(LineType.PART, null, null, name, qty, unitPrice, 0, List.of());
    }

    private static BigDecimal money(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || !v.isNumber()) {
            throw new IllegalStateException("price_list missing required number field: " + field);
        }
        return v.decimalValue().setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal optionalMoney(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isNumber()) {
            throw new IllegalStateException("price_list field must be number: " + field);
        }
        return v.decimalValue().setScale(2, RoundingMode.HALF_UP);
    }

    private static int intOr(JsonNode n, String field, int fallback) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return fallback;
        }
        return v.asInt(fallback);
    }

    private static String text(JsonNode n, String field, String fallback) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            return fallback;
        }
        return v.asText();
    }

    private static String moneyFmt(BigDecimal value) {
        return String.format(Locale.ROOT, "%,.0f", value).replace(',', ' ');
    }
}
