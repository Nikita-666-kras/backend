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
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KpHtmlPdfService {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM.yy");
    private static final Set<String> VAT0_CODES = Set.of("HD525", "HD540", "HD580", "T40", "T50");

    @Value("${storage.kp-dir:/data/kp}")
    private String kpDir;
    private final PartsCatalogClient partsCatalogClient;

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
        boolean vat0 = VAT0_CODES.contains(code);

        BigDecimal accessoriesTotal = p.grandTotal().subtract(p.dronePrice()).max(BigDecimal.ZERO);
        BigDecimal taxable = vat0 ? accessoriesTotal : p.grandTotal();
        BigDecimal ndsDeductible = taxable.multiply(BigDecimal.valueOf(22))
                .divide(BigDecimal.valueOf(122), 2, RoundingMode.HALF_UP);

        StringBuilder lines = new StringBuilder();
        int idx = 2;
        for (ProposalLineDto line : p.lines()) {
            lines.append("<tr>")
                    .append("<td class=\"num\">").append(String.format("%02d", idx++)).append("</td>")
                    .append("<td><strong>").append(esc(line.name())).append("</strong></td>")
                    .append("<td>").append(qtyLabel(line.qty())).append("</td>")
                    .append("<td><strong>").append(priceLabel(line)).append("</strong></td>")
                    .append("<td class=\"purpose\">").append(esc(purpose(line))).append("</td>")
                    .append("</tr>");
        }

        String kitDetailsSection = buildKitDetailsSection(p.lines());

        String dronePurpose = vat0
                ? "Основное воздушное судно *НДС 0% — подробнее стр. 2"
                : "Основное воздушное судно";
        String taxText = vat0
                ? "«БАС " + droneFullName + "» поставляется со ставкой НДС 0% согласно ст. 164 НК РФ. "
                + "Остальные позиции облагаются НДС 22%. Общая сумма к вычету: " + moneyDecimals(ndsDeductible) + " ₽."
                : "Все позиции комплекта облагаются НДС 22%. Общая сумма к вычету: "
                + moneyDecimals(ndsDeductible) + " ₽.";

        return template
                .replace("{{KP_NUMBER}}", String.valueOf(p.number()))
                .replace("{{RECIPIENT}}", esc(p.recipient()))
                .replace("{{DATE}}", LocalDate.now().format(DF))
                .replace("{{DRONE_FULL_NAME}}", esc(droneFullName))
                .replace("{{DRONE_PRICE}}", money(p.dronePrice()))
                .replace("{{DRONE_VAT_MARK}}", vat0 ? "*" : "")
                .replace("{{DRONE_PURPOSE}}", esc(dronePurpose))
                .replace("{{GRAND_TOTAL}}", money(p.grandTotal()))
                .replace("{{NDS_DEDUCTIBLE}}", moneyDecimals(ndsDeductible))
                .replace("{{TAX_TEXT}}", esc(taxText))
                .replace("{{TAGS_HTML}}", tagsHtml(code))
                .replace("{{LINES_HTML}}", lines.toString())
                .replace("{{KIT_DETAILS_SECTION}}", kitDetailsSection);
    }

    private String buildKitDetailsSection(List<ProposalLineDto> proposalLines) {
        var kitLines = proposalLines.stream()
                .filter(line -> line.lineType() == LineType.KIT)
                .toList();
        if (kitLines.isEmpty()) return "";

        var kitTables = new ArrayList<String>();
        for (ProposalLineDto kitLine : kitLines) {
            List<ProposalKitItemDto> items = resolveKitItems(kitLine);
            if (items.isEmpty()) continue;

            var table = new StringBuilder();
            String header = (kitLine.sku() != null && !kitLine.sku().isBlank())
                    ? kitLine.sku() + " · " + kitLine.name()
                    : kitLine.name();
            table.append("<div class=\"section-label\">— ").append(esc(header)).append("</div>");
            table.append("""
                    <div class="panel">
                      <table>
                        <thead>
                          <tr>
                            <th style="width:28px">№</th>
                            <th>Запчасть</th>
                            <th style="width:62px">Кол-во</th>
                            <th style="width:92px">Цена, ₽</th>
                            <th style="width:92px">Сумма, ₽</th>
                          </tr>
                        </thead>
                        <tbody>
                    """);
            int index = 1;
            for (ProposalKitItemDto item : items) {
                var price = item.partPrice() == null ? BigDecimal.ZERO : item.partPrice();
                int qty = item.qty() == null ? 0 : item.qty();
                var rowTotal = price.multiply(BigDecimal.valueOf(qty));
                table.append("<tr>")
                        .append("<td class=\"num\">").append(String.format("%02d", index++)).append("</td>")
                        .append("<td><strong>").append(esc(item.partSku())).append("</strong><br/>")
                        .append("<span class=\"purpose\">").append(esc(item.partName())).append("</span></td>")
                        .append("<td>").append(qtyLabel(qty)).append("</td>")
                        .append("<td>").append(money(price)).append("</td>")
                        .append("<td><strong>").append(money(rowTotal)).append("</strong></td>")
                        .append("</tr>");
            }
            table.append("""
                        </tbody>
                      </table>
                    </div>
                    """);
            kitTables.add(table.toString());
        }

        if (kitTables.isEmpty()) return "";

        var html = new StringBuilder();
        html.append("""
                <section class="page">
                  <header>
                    <img class="logo" src="static/LOGO_ATRIS.svg" alt="АТРИС"/>
                    <div class="contacts">
                      <strong>+7 (938) 119-29-82</strong><br/>
                      privet@atris.su<br/>
                      www.atris.su<br/>
                      Ростов-на-Дону
                    </div>
                  </header>
                  <div class="kicker">Приложение</div>
                  <div class="page-title">Состав комплекта</div>
                  <p class="page-lead">Детализация комплектов, указанных в коммерческом предложении.</p>
                """);
        for (String kitTable : kitTables) {
            html.append(kitTable);
        }
        html.append("""
                  <footer>
                    <span>Простые решения. Реальный результат.</span>
                    <span class="right">ООО «АТРИС»</span>
                  </footer>
                </section>
                """);
        return html.toString();
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
        if (line.lineType() == LineType.KIT) {
            return "Состав комплекта — в приложении на последней странице";
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
