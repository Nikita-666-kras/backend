package com.blog.platform.proposal.service;

import com.blog.platform.proposal.api.dto.KpDtos.ProposalDto;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalLineDto;
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

        String droneFullName = displayDroneName(p.droneModelName());
        BigDecimal accessoriesTotal = p.grandTotal().subtract(p.dronePrice()).max(BigDecimal.ZERO);
        BigDecimal ndsDeductible = accessoriesTotal.multiply(BigDecimal.valueOf(22))
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

        return template
                .replace("{{KP_NUMBER}}", String.valueOf(p.number()))
                .replace("{{RECIPIENT}}", esc(p.recipient()))
                .replace("{{DRONE_FULL_NAME}}", esc(droneFullName))
                .replace("{{DRONE_PRICE}}", money(p.dronePrice()))
                .replace("{{GRAND_TOTAL}}", money(p.grandTotal()))
                .replace("{{NDS_DEDUCTIBLE}}", moneyDecimals(ndsDeductible))
                .replace("{{DATE}}", LocalDate.now().format(DF))
                .replace("{{LINES_HTML}}", lines.toString());
    }

    private String displayDroneName(String name) {
        if (name == null || name.isBlank()) {
            return "VECTOR AGR";
        }
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.contains("VECTOR") || upper.contains("DJI") || upper.contains("AGRAS")) {
            return name;
        }
        if (upper.startsWith("HD") || upper.matches("HD\\d+.*")) {
            return "VECTOR AGR " + name;
        }
        if (upper.startsWith("T") && upper.length() <= 4) {
            return "DJI AGRAS " + name;
        }
        return name;
    }

    private String purpose(ProposalLineDto line) {
        String n = line.name() == null ? "" : line.name().toLowerCase(Locale.ROOT);
        if (n.contains("аккумулятор") || n.contains("акб") && n.contains("30")) {
            return "Автономная работа в поле";
        }
        if (n.contains("заряд") && n.contains("пульт")) {
            return "Зарядка АКБ пульта управления";
        }
        if (n.contains("заряд")) {
            return "Зарядка батарей в полевых условиях";
        }
        if (n.contains("пульт") && n.contains("акб")) {
            return "Питание пульта управления";
        }
        if (n.contains("пилот")) {
            return "Профессиональное пилотирование на объёме хозяйства";
        }
        if (n.contains("распыл") || n.contains("форсун")) {
            return "Распылительная система";
        }
        if (n.contains("пропеллер") || n.contains("лопаст")) {
            return "Расходный элемент";
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
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String sanitize(String value) {
        if (value == null) {
            return "model";
        }
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
