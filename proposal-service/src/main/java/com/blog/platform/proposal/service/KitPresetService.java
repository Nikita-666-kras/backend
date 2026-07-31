package com.blog.platform.proposal.service;

import com.blog.platform.proposal.api.dto.KpDtos.KitPresetDto;
import com.blog.platform.proposal.api.dto.KpDtos.KitPresetLineDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KitPresetService {

    private final KpPcCalculatorService calculator;

    public KitPresetDto presetFor(String codeOrName) {
        var price = calculator.requirePrice(codeOrName);
        var calc = calculator.calculate(codeOrName, 1, price.startPrice());
        List<KitPresetLineDto> lines = calc.lines().stream()
                .map(l -> new KitPresetLineDto(
                        l.lineType(), l.refId(), l.sku(), l.name(), l.qty(), l.unitPrice(), l.discountPct()))
                .toList();
        return new KitPresetDto(calc.priceKey(), calc.unitDronePrice(), calc.startPrice(), calc.vatMode(), lines);
    }
}
