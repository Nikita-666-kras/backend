package com.blog.platform.proposal.service;

import com.blog.platform.proposal.api.dto.KpDtos.ProposalDto;
import com.blog.platform.proposal.api.dto.KpDtos.LineType;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalKitItemDto;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalLineDto;
import com.blog.platform.proposal.client.PartsCatalogClient;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KpHtmlPdfService {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM.yy");

    @Value("${storage.kp-dir:/data/kp}")
    private String kpDir;
    private final PartsCatalogClient partsCatalogClient;
    private final KpPcCalculatorService calculator;

    public String generate(ProposalDto proposal) {
        try {
            Files.createDirectories(Path.of(kpDir));
            String html = buildHtml(proposal);
            String droneLabel = sanitize(displayDroneName(proposal.droneModelName()));
            String fileName = "KP_" + proposal.number() + "_" + LocalDate.now().format(DF) + "_" + droneLabel + ".pdf";
            Path out = Path.of(kpDir, fileName);

            byte[] regBytes;
            byte[] boldBytes;
            try (InputStream regIn = new ClassPathResource("fonts/arial.ttf").getInputStream();
                 InputStream boldIn = new ClassPathResource("fonts/arialbd.ttf").getInputStream()) {
                regBytes = regIn.readAllBytes();
                boldBytes = boldIn.readAllBytes();
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.useSVGDrawer(new BatikSVGDrawer());
                builder.useFont(() -> new java.io.ByteArrayInputStream(regBytes), "Arial",
                        400, PdfRendererBuilder.FontStyle.NORMAL, true);
                builder.useFont(() -> new java.io.ByteArrayInputStream(boldBytes), "Arial",
                        700, PdfRendererBuilder.FontStyle.NORMAL, true);
                builder.useFont(() -> new java.io.ByteArrayInputStream(boldBytes), "Arial",
                        800, PdfRendererBuilder.FontStyle.NORMAL, true);
                String baseUri = new ClassPathResource("").getURL().toExternalForm();
                builder.withHtmlContent(html, baseUri);
                builder.toStream(baos);
                builder.run();
                Files.write(out, baos.toByteArray());
            }
            return out.toAbsolutePath().toString();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF generation failed", ex);
        }
    }

    private String buildHtml(ProposalDto p) throws Exception {
        String template;
        try (InputStream in = new ClassPathResource("templates/kp-atris.html").getInputStream()) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        String code = modelCode(p.droneModelName());
        String droneFullName = displayDroneName(p.droneModelName());
        int kitQty = p.kitQty() == null || p.kitQty() < 1 ? 1 : p.kitQty();
        boolean mixedVat = p.droneVatPct() != null
                ? p.droneVatPct() == 0
                : isMixedVat(code, p.droneModelName());

        BigDecimal droneUnit = p.dronePrice() == null ? BigDecimal.ZERO : p.dronePrice();
        BigDecimal droneTotal = droneUnit.multiply(BigDecimal.valueOf(kitQty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ndsDeductible = p.ndsTotal() != null
                ? p.ndsTotal()
                : (mixedVat ? p.grandTotal().subtract(droneTotal).max(BigDecimal.ZERO) : p.grandTotal())
                .multiply(BigDecimal.valueOf(22))
                .divide(BigDecimal.valueOf(122), 2, RoundingMode.HALF_UP);

        StringBuilder lines = new StringBuilder();
        int idx = 2;
        for (ProposalLineDto line : p.lines()) {
            BigDecimal unit = line.unitPrice() == null ? BigDecimal.ZERO : line.unitPrice();
            BigDecimal lineTotal = line.lineTotal() != null
                    ? line.lineTotal()
                    : unit.multiply(BigDecimal.valueOf(line.qty() == null ? 0 : line.qty()));
            boolean free = lineTotal.compareTo(BigDecimal.ZERO) == 0 || unit.compareTo(BigDecimal.ZERO) == 0;
            String sumCell = free ? "В цене" : money(lineTotal);
            String unitHint = (!free && line.qty() != null && line.qty() > 1)
                    ? "<span class=\"unit-hint\">" + money(unit) + " ₽ / шт.</span>"
                    : "";
            String typeHint = line.lineType() == LineType.KIT
                    ? (isZipPackageLine(line) ? "ЗИП-пакет" : "комплект")
                    : "";
            lines.append("<tr>")
                    .append("<td class=\"num\">").append(String.format("%02d", idx++)).append("</td>")
                    .append("<td><strong>").append(esc(line.name())).append("</strong>");
            if (!typeHint.isBlank()) {
                lines.append("<span class=\"unit-hint\">").append(esc(typeHint)).append("</span>");
            }
            lines.append(unitHint).append("</td>")
                    .append("<td class=\"qty-cell\">").append(qtyLabel(line.qty() == null ? 0 : line.qty())).append("</td>")
                    .append("<td class=\"price-cell\"><strong>").append(sumCell).append("</strong></td>")
                    .append("<td class=\"purpose\">").append(esc(purpose(line))).append("</td>")
                    .append("</tr>");
        }

        String kitDetailsSection = buildKitDetailsSection(p);

        String dronePurpose = mixedVat
                ? "Основное воздушное судно *НДС 0% — подробнее стр. 2"
                : "Основное воздушное судно · НДС 22%";
        String taxText = mixedVat
                ? "«БАС " + droneFullName + "» поставляется со ставкой НДС 0% согласно ст. 164 НК РФ. "
                + "Остальные позиции облагаются НДС 22%. Общая сумма к вычету: " + moneyDecimals(ndsDeductible) + " ₽."
                : "БАС и остальные позиции облагаются НДС 22%. Общая сумма к вычету: "
                + moneyDecimals(ndsDeductible) + " ₽.";
        String costText = mixedVat
                ? money(p.grandTotal()) + " ₽. В итоговую сумму входит БАС без НДС и позиции с НДС 22%."
                : money(p.grandTotal()) + " ₽, с учётом НДС 22%.";
        String totalHint = mixedVat
                ? "БАС: НДС 0% · остальное: НДС 22%"
                : "НДС 22% на все позиции, включая БАС";
        String droneUnitHint = kitQty > 1
                ? money(droneUnit) + " ₽ / шт."
                : (mixedVat ? "НДС 0%" : "НДС 22%");

        return template
                .replace("{{KP_NUMBER}}", String.valueOf(p.number()))
                .replace("{{RECIPIENT}}", esc(p.recipient()))
                .replace("{{DATE}}", LocalDate.now().format(DF))
                .replace("{{DRONE_FULL_NAME}}", esc(droneFullName))
                .replace("{{DRONE_QTY}}", qtyLabel(kitQty))
                .replace("{{DRONE_LINE_TOTAL}}", money(droneTotal))
                .replace("{{DRONE_UNIT_HINT}}", esc(droneUnitHint))
                .replace("{{DRONE_VAT_MARK}}", mixedVat ? "*" : "")
                .replace("{{DRONE_PURPOSE}}", esc(dronePurpose))
                .replace("{{GRAND_TOTAL}}", money(p.grandTotal()))
                .replace("{{NDS_DEDUCTIBLE}}", moneyDecimals(ndsDeductible))
                .replace("{{TAX_TEXT}}", esc(taxText))
                .replace("{{COST_TEXT}}", esc(costText))
                .replace("{{TOTAL_HINT}}", esc(totalHint))
                .replace("{{TAGS_HTML}}", tagsHtml(code))
                .replace("{{LINES_HTML}}", lines.toString())
                .replace("{{KIT_DETAILS_SECTION}}", kitDetailsSection);
    }

    private boolean isMixedVat(String code, String modelName) {
        var price = calculator.findPrice(code != null ? code : "");
        if (price == null) {
            price = calculator.findPrice(modelName);
        }
        if (price == null) {
            return false;
        }
        return "mixed".equalsIgnoreCase(price.vatMode());
    }

    private static final String ZIP_SKU_PREFIX = "ZIP-";

    private boolean isZipPackageLine(ProposalLineDto line) {
        return line.lineType() == LineType.KIT
                && line.sku() != null
                && line.sku().regionMatches(true, 0, ZIP_SKU_PREFIX, 0, ZIP_SKU_PREFIX.length());
    }

    /**
     * Состав ЗИП встраивается на страницу 3 (без отдельной страницы-приложения).
     * Пустая строка, если ЗИП в КП нет.
     */
    private String buildKitDetailsSection(ProposalDto p) {
        var kitTables = new ArrayList<String>();

        for (ProposalLineDto kitLine : p.lines()) {
            if (!isZipPackageLine(kitLine)) {
                continue;
            }
            List<ProposalKitItemDto> items = resolveKitItems(kitLine);
            if (items.isEmpty()) {
                continue;
            }
            String header = (kitLine.name() == null || kitLine.name().isBlank())
                    ? "ЗИП-пакет"
                    : kitLine.name();
            kitTables.add(kitItemsTable(header, items));
        }

        if (kitTables.isEmpty()) {
            return "";
        }

        var html = new StringBuilder();
        html.append("""
                <div class="zip-block">
                  <div class="section-label">— Состав ЗИП-комплекта</div>
                """);
        for (String kitTable : kitTables) {
            html.append(kitTable);
        }
        html.append("</div>");
        return html.toString();
    }

    private String kitItemsTable(String header, List<ProposalKitItemDto> items) {
        var table = new StringBuilder();
        table.append("<p class=\"zip-kit-name\">").append(esc(header)).append("</p>");
        table.append("""
                <div class="panel zip-panel">
                  <table>
                    <thead>
                      <tr>
                        <th style="width:28px">№</th>
                        <th>Запчасть</th>
                        <th class="col-qty" style="width:54px">Кол-во</th>
                        <th class="col-sum" style="width:78px">Цена, ₽</th>
                        <th class="col-sum" style="width:78px">Сумма, ₽</th>
                      </tr>
                    </thead>
                    <tbody>
                """);
        int index = 1;
        for (ProposalKitItemDto item : items) {
            var price = item.partPrice() == null ? BigDecimal.ZERO : item.partPrice();
            int qty = item.qty() == null ? 0 : item.qty();
            var rowTotal = price.multiply(BigDecimal.valueOf(qty));
            String sku = item.partSku() == null || item.partSku().isBlank() ? "" : item.partSku();
            String name = item.partName() == null ? "" : item.partName();
            table.append("<tr>")
                    .append("<td class=\"num\">").append(String.format("%02d", index++)).append("</td>")
                    .append("<td>");
            if (!sku.isBlank()) {
                table.append("<strong>").append(esc(sku)).append("</strong>");
                if (!name.isBlank()) {
                    table.append("<br/><span class=\"purpose\">").append(esc(name)).append("</span>");
                }
            } else {
                table.append("<strong>").append(esc(name)).append("</strong>");
            }
            table.append("</td>")
                    .append("<td class=\"qty-cell\">").append(qtyLabel(qty)).append("</td>")
                    .append("<td class=\"price-cell\">").append(money(price)).append("</td>")
                    .append("<td class=\"price-cell\"><strong>").append(money(rowTotal)).append("</strong></td>")
                    .append("</tr>");
        }
        table.append("""
                    </tbody>
                  </table>
                </div>
                """);
        return table.toString();
    }

    private List<ProposalKitItemDto> resolveKitItems(ProposalLineDto kitLine) {
        if (kitLine.kitItems() != null && !kitLine.kitItems().isEmpty()) {
            return kitLine.kitItems();
        }
        if (kitLine.refId() == null) return List.of();
        try {
            var detail = partsCatalogClient.getKitById(kitLine.refId());
            if (detail.items() == null || detail.items().isEmpty()) return List.of();
            return detail.items().stream()
                    .map(i -> new ProposalKitItemDto(
                            i.partId(),
                            i.partSku(),
                            i.partName(),
                            i.qty(),
                            i.partPrice()))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String tagsHtml(String code) {
        String ha = switch (code) {
            case "T40", "T50" -> "15 га/час";
            default -> "12 га/час";
        };
        return """
                <span class="tag">Внесение СЗР</span>
                <span class="tag">Десикация</span>
                <span class="tag">Разбрасывание</span>
                <span class="tag">%s</span>
                <span class="tag">С регистрацией</span>
                """.formatted(ha);
    }

    private String modelCode(String name) {
        if (name == null) return "";
        String upper = name.toUpperCase(Locale.ROOT).replace("VECTOR AGR ", "").replace("DJI AGRAS ", "").trim();
        if (upper.contains("HD580")) return "HD580";
        if (upper.contains("HD540")) return "HD540";
        if (upper.contains("HD525")) return "HD525";
        if (upper.contains("T50")) return "T50";
        if (upper.contains("T40")) return "T40";
        if (upper.contains("T30")) return "T30";
        return upper;
    }

    private String displayDroneName(String name) {
        if (name == null || name.isBlank()) return "VECTOR AGR";
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.contains("VECTOR") || upper.contains("DJI") || upper.contains("AGRAS")) return name;
        if (upper.startsWith("HD")) return "VECTOR AGR " + name;
        if (upper.startsWith("T") && upper.length() <= 4) return "DJI AGRAS " + name;
        return name;
    }

    private String purpose(ProposalLineDto line) {
        String n = line.name() == null ? "" : line.name().toLowerCase(Locale.ROOT);
        if (n.contains("генератор")) {
            return "Зарядка батарей в полевых условиях";
        }
        if (n.contains("заряд") || n.contains("устройство заряд") || n.contains("станция")) {
            if (n.contains("пульт") || n.contains("wb37")) {
                return "Зарядка АКБ пульта управления";
            }
            return "Зарядка батарей в полевых условиях";
        }
        if (n.contains("аккумулятор") || n.contains("батарея") || n.contains("battery")) {
            if (n.contains("пульт") || n.contains("wb37")) {
                return "Питание пульта управления";
            }
            return "Автономная работа в поле";
        }
        if (n.contains("акб")) {
            if (n.contains("пульт") || n.contains("wb37")) {
                return "Питание пульта управления";
            }
            return "Автономная работа в поле";
        }
        if (n.contains("пульт") || n.contains("wb37")) {
            return "Питание пульта управления";
        }
        if (isZipPackageLine(line)) {
            return "Состав ЗИП — на стр. 3";
        }
        if (line.lineType() == LineType.KIT) {
            return "Комплект поставки";
        }
        return "Комплектация и сопровождение";
    }

    private String qtyLabel(int qty) {
        return qty + " шт.";
    }

    private String priceLabel(ProposalLineDto line) {
        if (line.lineTotal().compareTo(BigDecimal.ZERO) == 0
                || line.unitPrice().compareTo(BigDecimal.ZERO) == 0) {
            return "В цене";
        }
        return money(line.lineTotal());
    }

    private String money(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("ru", "RU"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(value.setScale(0, RoundingMode.HALF_UP));
    }

    private String moneyDecimals(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("ru", "RU"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(value);
    }

    private String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String sanitize(String value) {
        if (value == null) return "model";
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
