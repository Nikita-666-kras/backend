package com.blog.platform.proposal.service;

import com.blog.platform.proposal.api.dto.KpDtos;
import com.blog.platform.proposal.api.dto.KpDtos.DroneModelDto;
import com.blog.platform.proposal.api.dto.KpDtos.DroneModelUpsertRequest;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalDto;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalLineDto;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalUpsertRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProposalService {
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM.yy");
    private final JdbcTemplate jdbc;
    @Value("${storage.kp-dir:/data/kp}")
    private String kpDir;

    public List<DroneModelDto> listModels(boolean onlyActive) {
        String sql = "select id, code, name, default_price, sort_order, active from kp_drone_models "
                + (onlyActive ? "where active = true " : "")
                + "order by sort_order asc, name asc";
        return jdbc.query(sql, (rs, i) -> new DroneModelDto(
                rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getBigDecimal("default_price"), rs.getInt("sort_order"), rs.getBoolean("active")));
    }

    @Transactional
    public DroneModelDto upsertModel(UUID id, DroneModelUpsertRequest req) {
        UUID modelId = id == null ? UUID.randomUUID() : id;
        int updated = jdbc.update("""
                update kp_drone_models set code=?, name=?, default_price=?, sort_order=?, active=?, updated_at=now() where id=?
                """, req.code().trim(), req.name().trim(), req.defaultPrice(),
                req.sortOrder() == null ? 0 : req.sortOrder(), req.active() == null || req.active(), modelId);
        if (updated == 0) {
            jdbc.update("""
                    insert into kp_drone_models(id, code, name, default_price, sort_order, active, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, now(), now())
                    """, modelId, req.code().trim(), req.name().trim(), req.defaultPrice(),
                    req.sortOrder() == null ? 0 : req.sortOrder(), req.active() == null || req.active());
        }
        return listModels(false).stream().filter(x -> x.id().equals(modelId)).findFirst().orElseThrow();
    }

    @Transactional
    public ProposalDto saveDraft(UUID managerId, String username, UUID proposalId, ProposalUpsertRequest req) {
        UUID id = proposalId == null ? UUID.randomUUID() : proposalId;
        var model = listModels(false).stream().filter(x -> x.id().equals(req.droneModelId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Drone model not found"));
        Totals totals = totals(req.dronePrice(), req.lines());
        if (proposalId == null) {
            Integer number = jdbc.queryForObject("select last_kp_number from kp_settings where id=1 for update", Integer.class);
            int next = number == null ? 1 : number + 1;
            jdbc.update("update kp_settings set last_kp_number=? where id=1", next);
            jdbc.update("""
                    insert into kp_proposals(id, number, manager_id, manager_username, recipient, drone_model_id, drone_model_name, drone_price, status,
                    subtotal, discount_total, grand_total, nds_total, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, now(), now())
                    """, id, next, managerId, username, req.recipient(), req.droneModelId(), model.name(), req.dronePrice(),
                    totals.subtotal, totals.discount, totals.grand, totals.nds);
        } else {
            jdbc.update("""
                    update kp_proposals set recipient=?, drone_model_id=?, drone_model_name=?, drone_price=?, status='DRAFT',
                    subtotal=?, discount_total=?, grand_total=?, nds_total=?, updated_at=now()
                    where id=? and manager_id=?
                    """, req.recipient(), req.droneModelId(), model.name(), req.dronePrice(),
                    totals.subtotal, totals.discount, totals.grand, totals.nds, id, managerId);
            jdbc.update("delete from kp_proposal_lines where proposal_id=?", id);
        }
        insertLines(id, req.lines());
        return getById(id, managerId, false);
    }

    @Transactional
    public ProposalDto finalizeProposal(UUID id, UUID managerId, boolean adminMode) {
        ProposalDto proposal = getById(id, managerId, adminMode);
        String pdf = generatePdf(proposal);
        jdbc.update("update kp_proposals set status='FINAL', pdf_path=?, updated_at=now() where id=?", pdf, id);
        return getById(id, managerId, adminMode);
    }

    public List<ProposalDto> listForManager(UUID managerId) {
        return list("where manager_id=? order by updated_at desc", managerId);
    }

    public List<ProposalDto> listAll() {
        return list("order by updated_at desc limit 500");
    }

    public ProposalDto getById(UUID id, UUID managerId, boolean adminMode) {
        String sql = "select * from kp_proposals where id=?" + (adminMode ? "" : " and manager_id=?");
        List<ProposalDto> rows = adminMode
                ? jdbc.query(sql, (rs, i) -> mapProposal(rs.getObject("id", UUID.class), rs), id)
                : jdbc.query(sql, (rs, i) -> mapProposal(rs.getObject("id", UUID.class), rs), id, managerId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Proposal not found");
        return rows.get(0);
    }

    public Path pdfPath(UUID id, UUID managerId, boolean adminMode) {
        ProposalDto proposal = getById(id, managerId, adminMode);
        if (proposal.pdfPath() == null || proposal.pdfPath().isBlank()) throw new IllegalArgumentException("PDF not generated");
        return Path.of(proposal.pdfPath());
    }

    private List<ProposalDto> list(String tail, Object... args) {
        return jdbc.query("select * from kp_proposals " + tail, (rs, i) -> mapProposal(rs.getObject("id", UUID.class), rs), args);
    }

    private ProposalDto mapProposal(UUID proposalId, java.sql.ResultSet rs) throws java.sql.SQLException {
        List<ProposalLineDto> lines = jdbc.query("select * from kp_proposal_lines where proposal_id=?", (lr, i) -> new ProposalLineDto(
                lr.getObject("id", UUID.class),
                KpDtos.LineType.valueOf(lr.getString("line_type")),
                lr.getObject("ref_id", UUID.class),
                lr.getString("sku"),
                lr.getString("name"),
                lr.getInt("qty"),
                lr.getBigDecimal("unit_price"),
                lr.getInt("discount_pct"),
                lr.getBigDecimal("line_total")
        ), proposalId);
        return new ProposalDto(
                proposalId, rs.getInt("number"), rs.getObject("manager_id", UUID.class), rs.getString("manager_username"),
                rs.getString("recipient"), rs.getObject("drone_model_id", UUID.class), rs.getString("drone_model_name"),
                rs.getBigDecimal("drone_price"), rs.getString("status"),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_total"), rs.getBigDecimal("grand_total"), rs.getBigDecimal("nds_total"),
                rs.getString("pdf_path"), lines, toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("updated_at")));
    }

    private void insertLines(UUID proposalId, List<KpDtos.ProposalLineRequest> lines) {
        for (var l : lines) {
            int discount = l.lineType() == KpDtos.LineType.KIT ? normalizeDiscount(l.discountPct()) : 0;
            BigDecimal raw = l.unitPrice().multiply(BigDecimal.valueOf(l.qty()));
            BigDecimal total = raw.multiply(BigDecimal.valueOf(100 - discount)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            jdbc.update("""
                    insert into kp_proposal_lines(id, proposal_id, line_type, ref_id, sku, name, qty, unit_price, discount_pct, line_total)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), proposalId, l.lineType().name(), l.refId(), l.sku(), l.name(), l.qty(), l.unitPrice(), discount, total);
        }
    }

    private Totals totals(BigDecimal dronePrice, List<KpDtos.ProposalLineRequest> lines) {
        BigDecimal subtotal = dronePrice;
        BigDecimal discount = BigDecimal.ZERO;
        for (var l : lines) {
            int d = l.lineType() == KpDtos.LineType.KIT ? normalizeDiscount(l.discountPct()) : 0;
            BigDecimal raw = l.unitPrice().multiply(BigDecimal.valueOf(l.qty()));
            subtotal = subtotal.add(raw);
            if (d > 0) discount = discount.add(raw.multiply(BigDecimal.valueOf(d)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        BigDecimal grand = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nds = grand.multiply(BigDecimal.valueOf(22)).divide(BigDecimal.valueOf(122), 2, RoundingMode.HALF_UP);
        return new Totals(subtotal.setScale(2, RoundingMode.HALF_UP), discount.setScale(2, RoundingMode.HALF_UP), grand, nds);
    }

    private int normalizeDiscount(Integer pct) {
        if (pct == null) return 0;
        if (pct == 0 || pct == 5 || pct == 10 || pct == 15 || pct == 20) return pct;
        throw new IllegalArgumentException("Discount must be 0/5/10/15/20");
    }

    private String generatePdf(ProposalDto p) {
        try {
            Files.createDirectories(Path.of(kpDir));
            String file = "КП №" + p.number() + " от " + LocalDate.now().format(DF) + " (" + p.droneModelName() + ") - АТРИС.pdf";
            Path path = Path.of(kpDir, file);
            try (PDDocument doc = new PDDocument()) {
                PdfNumbers n = deriveNumbers(p);

                // Page 1 - variant 01 + transition green block
                PDPage page1 = new PDPage(PDRectangle.A4);
                doc.addPage(page1);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                    drawHeader(cs, "KOMMERCHESKOE PREDLOZHENIE · VARIANT 01");
                    text(cs, 50, 760, 14, true, "Aviaobrabotka poley");
                    text(cs, 50, 740, 11, false, "Poluchatel: " + safeAscii(p.recipient()));
                    text(cs, 50, 722, 11, false, "Raschet dlya ploshadi " + n.areaHa + " ga.");
                    text(cs, 50, 690, 11, false, "Stavka: 1 000 RUB/ga");
                    text(cs, 50, 672, 12, true, "Na obyom " + n.areaHa + " ga: " + money(n.variantOneTotal));
                    drawGreenBlock(cs, 50, 520, 495, 120,
                            "Est bolee vygodnyy format dlya " + n.areaHa + " ga",
                            "HD580 + professional pilot: " + money(n.variantTwoTotal)
                                    + ", ekonomiya okolo " + money(n.saving) + " uzhe v pervyy sezon.");
                    footer(cs, 1);
                }

                // Page 2 - variant 02 + guarantee block
                PDPage page2 = new PDPage(PDRectangle.A4);
                doc.addPage(page2);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                    drawHeader(cs, "VARIANT 02 · REKOMENDUEM");
                    text(cs, 50, 760, 14, true, p.droneModelName() + " + professional pilot");
                    text(cs, 50, 740, 11, false, "Komplekt: " + money(p.dronePrice()));
                    text(cs, 50, 722, 11, false, "Pilot: 100 RUB/ga, na " + n.areaHa + " ga = " + money(n.pilotTotal));
                    text(cs, 50, 704, 12, true, "Itogo na sezon: " + money(n.variantTwoTotal));
                    int y = 670;
                    for (ProposalLineDto line : p.lines()) {
                        text(cs, 50, y, 10, false,
                                line.lineType() + " | " + line.name() + " | qty " + line.qty()
                                        + " | " + money(line.lineTotal()) + " | skidka " + line.discountPct() + "%");
                        y -= 16;
                        if (y < 490) {
                            break;
                        }
                    }
                    drawGreenBlock(cs, 50, 350, 495, 120,
                            "Garantiya otvetstvennosti pilota",
                            "Pri polomkah po vine pilota OOO ATRIS kompaniya pokryvaet remont drona zakazchika.");
                    footer(cs, 2);
                }

                // Page 3 - comparison
                PDPage page3 = new PDPage(PDRectangle.A4);
                doc.addPage(page3);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page3)) {
                    drawHeader(cs, "SRAVNENIE NA " + n.areaHa + " GA");
                    text(cs, 50, 760, 12, true, "Variant 01: " + money(n.variantOneTotal));
                    text(cs, 50, 740, 12, true, "Variant 02: " + money(n.variantTwoTotal));
                    text(cs, 50, 720, 12, true, "Ekonomiya: " + money(n.saving));
                    text(cs, 50, 688, 11, false, "1) Nizhe stoimost pervogo sezona.");
                    text(cs, 50, 670, 11, false, "2) Tehnika ostaetsya na balanse hozyaystva.");
                    text(cs, 50, 652, 11, false, "3) Pilot oplachivaetsya tolko po fakticheskim ga.");
                    text(cs, 50, 634, 11, false, "4) Riski pilotirovaniya po vine pilota pokryvaet ATRIS.");
                    footer(cs, 3);
                }

                // Page 4 - terms
                PDPage page4 = new PDPage(PDRectangle.A4);
                doc.addPage(page4);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page4)) {
                    drawHeader(cs, "USLOVIYA PREDLOZHENIYA");
                    text(cs, 50, 760, 11, false, "Srok deystviya predlozheniya - 15 rabochih dney.");
                    text(cs, 50, 742, 11, false, "Variant 01: " + money(n.variantOneTotal) + " pri stavke 1 000 RUB/ga.");
                    text(cs, 50, 724, 11, false, "Variant 02: " + p.droneModelName() + " + pilot = " + money(n.variantTwoTotal) + ".");
                    text(cs, 50, 706, 11, false, "Oplata i postavka soglasovyvayutsya dogovorom.");
                    text(cs, 50, 688, 11, false, "Garantiya: 12 mesyatsev. NDS vklju chen: " + money(p.ndsTotal()) + ".");
                    drawGreenBlock(cs, 50, 560, 495, 90,
                            "Rekomenduem dlya " + n.areaHa + " ga",
                            p.droneModelName() + " + pilot: " + money(n.variantTwoTotal) + ". Ekonomiya: " + money(n.saving));
                    footer(cs, 4);
                }
                doc.save(path.toFile());
            }
            return path.toAbsolutePath().toString();
        } catch (IOException ex) {
            throw new IllegalStateException("PDF generation failed", ex);
        }
    }

    private Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private PdfNumbers deriveNumbers(ProposalDto p) {
        BigDecimal pilotTotal = BigDecimal.ZERO;
        for (ProposalLineDto line : p.lines()) {
            String lower = (line.name() == null ? "" : line.name().toLowerCase());
            if (lower.contains("пилот") || lower.contains("pilot")) {
                pilotTotal = pilotTotal.add(line.lineTotal());
            }
        }
        int area = 4200;
        if (pilotTotal.compareTo(BigDecimal.ZERO) > 0) {
            area = pilotTotal.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).intValue();
        }
        BigDecimal variantOne = BigDecimal.valueOf(area).multiply(BigDecimal.valueOf(1000));
        BigDecimal variantTwo = p.grandTotal();
        BigDecimal saving = variantOne.subtract(variantTwo);
        return new PdfNumbers(area, pilotTotal, variantOne, variantTwo, saving);
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " RUB";
    }

    private void drawHeader(PDPageContentStream cs, String title) throws IOException {
        text(cs, 50, 805, 10, false, "+7 (938) 119-29-82 · privet@atris.su · www.atris.su");
        text(cs, 50, 785, 12, true, title);
    }

    private void footer(PDPageContentStream cs, int pageNo) throws IOException {
        text(cs, 50, 26, 10, false, "INZHENERNYE SISTEMY AGROPRIMENENIYA · OOO ATRIS · STR. 0" + pageNo);
    }

    private void drawGreenBlock(PDPageContentStream cs, float x, float y, float w, float h, String title, String body) throws IOException {
        cs.setNonStrokingColor(141, 198, 63);
        cs.addRect(x, y, w, h);
        cs.fill();
        cs.setNonStrokingColor(6, 37, 84);
        text(cs, x + 12, y + h - 26, 12, true, title);
        text(cs, x + 12, y + h - 46, 10, false, body);
        cs.setNonStrokingColor(0, 0, 0);
    }

    private void text(PDPageContentStream cs, float x, float y, int fontSize, boolean bold, String text) throws IOException {
        cs.beginText();
        cs.setFont(bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, fontSize);
        cs.setRenderingMode(RenderingMode.FILL);
        cs.newLineAtOffset(x, y);
        cs.showText(safeAscii(text));
        cs.endText();
    }

    private String safeAscii(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('«', '"')
                .replace('»', '"')
                .replace('—', '-')
                .replace('ё', 'e')
                .replace('Ё', 'E')
                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    private record PdfNumbers(int areaHa, BigDecimal pilotTotal, BigDecimal variantOneTotal, BigDecimal variantTwoTotal, BigDecimal saving) {}

    private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal grand, BigDecimal nds) {}
}
