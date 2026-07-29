package com.blog.platform.proposal.service;

import com.blog.platform.proposal.api.dto.KpDtos;
import com.blog.platform.proposal.api.dto.KpDtos.KitPresetDto;
import com.blog.platform.proposal.api.dto.KpDtos.KitPresetLineDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class KitPresetService {

    private record Preset(BigDecimal dronePrice, List<KitPresetLineDto> lines) {}

    private static final Map<String, Preset> PRESETS = Map.of(
            "HD525", new Preset(bd("1105000"), List.of(
                    line("АККУМУЛЯТОР ЛИТИЙ-ИОННЫЙ HE102 (30Ah)", 3, "230000"),
                    line("УСТРОЙСТВО ЗАРЯДНОЕ HE202 ДЛЯ АКБ ДРОНА", 1, "205000"),
                    line("ЗАРЯДНАЯ СТАНЦИЯ ДЛЯ АКБ ПУЛЬТА", 1, "0"),
                    line("АКБ ДЛЯ ПУЛЬТА", 2, "0")
            )),
            "HD540", new Preset(bd("1550000"), List.of(
                    line("АККУМУЛЯТОР ЛИТИЙ-ИОННЫЙ ZAB1830", 3, "215000"),
                    line("УСТРОЙСТВО ЗАРЯДНОЕ HE202 ДЛЯ АКБ ДРОНА", 1, "205000"),
                    line("ЗАРЯДНАЯ СТАНЦИЯ ДЛЯ АКБ ПУЛЬТА", 1, "0"),
                    line("АКБ ДЛЯ ПУЛЬТА", 2, "0")
            )),
            "HD580", new Preset(bd("2305000"), List.of(
                    line("АККУМУЛЯТОР ЛИТИЙ-ИОННЫЙ HE102 (30Ah)", 3, "230000"),
                    line("УСТРОЙСТВО ЗАРЯДНОЕ HE202 ДЛЯ АКБ ДРОНА", 1, "205000"),
                    line("ЗАРЯДНАЯ СТАНЦИЯ ДЛЯ АКБ ПУЛЬТА", 1, "0"),
                    line("АКБ ДЛЯ ПУЛЬТА", 2, "0")
            )),
            "T30", new Preset(bd("1147000"), List.of(
                    line("ИНТЕЛЛЕКТУАЛЬНАЯ БАТАРЕЯ ДРОНА", 3, "205000"),
                    line("ЗАРЯДНАЯ СТАНЦИЯ ДЛЯ АКБ ДРОНА", 1, "220000"),
                    line("WB37 INTELLIGENT BATTERY", 1, "18000")
            )),
            "T40", new Preset(bd("1795000"), List.of(
                    line("ИНТЕЛЛЕКТУАЛЬНАЯ БАТАРЕЯ DB1560", 3, "255000"),
                    line("ГЕНЕРАТОР DJI D12500iE", 1, "494500"),
                    line("ЗАРЯДНАЯ СТАНЦИЯ ДЛЯ WB37", 1, "16500"),
                    line("WB37 INTELLIGENT BATTERY", 2, "14500")
            )),
            "T50", new Preset(bd("2180000"), List.of(
                    line("ИНТЕЛЛЕКТУАЛЬНАЯ БАТАРЕЯ DB1560", 3, "255000"),
                    line("ГЕНЕРАТОР DJI D12500iE", 1, "494500"),
                    line("ЗАРЯДНАЯ СТАНЦИЯ ДЛЯ WB37", 1, "16500"),
                    line("WB37 INTELLIGENT BATTERY", 2, "14500")
            ))
    );

    public KitPresetDto presetFor(String codeOrName) {
        String code = normalize(codeOrName);
        Preset preset = PRESETS.get(code);
        if (preset == null) {
            throw new IllegalArgumentException("No kit preset for model: " + codeOrName);
        }
        return new KitPresetDto(code, preset.dronePrice(), preset.lines());
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String upper = value.toUpperCase(Locale.ROOT)
                .replace("VECTOR AGR ", "")
                .replace("DJI AGRAS ", "")
                .trim();
        if (upper.contains("HD580")) return "HD580";
        if (upper.contains("HD540")) return "HD540";
        if (upper.contains("HD525")) return "HD525";
        if (upper.contains("T50")) return "T50";
        if (upper.contains("T40")) return "T40";
        if (upper.contains("T30")) return "T30";
        return upper;
    }

    private static KitPresetLineDto line(String name, int qty, String unitPrice) {
        return new KitPresetLineDto(KpDtos.LineType.PART, null, null, name, qty, bd(unitPrice), 0);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
