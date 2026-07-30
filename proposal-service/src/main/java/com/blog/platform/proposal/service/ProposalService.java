package com.blog.platform.proposal.service;

import com.blog.platform.proposal.api.dto.KpDtos;
import com.blog.platform.proposal.api.dto.KpDtos.DroneModelDto;
import com.blog.platform.proposal.api.dto.KpDtos.DroneModelUpsertRequest;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalDto;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalKitItemDto;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalLineDto;
import com.blog.platform.proposal.api.dto.KpDtos.ProposalUpsertRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProposalService {
    private final JdbcTemplate jdbc;
    private final KpHtmlPdfService kpHtmlPdfService;
    private final ObjectMapper objectMapper;

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
    public void deleteModel(UUID id) {
        Integer used = jdbc.queryForObject(
                "select count(*) from kp_proposals where drone_model_id=?", Integer.class, id);
        if (used != null && used > 0) {
            throw new IllegalArgumentException(
                    "Нельзя удалить модель: она используется в " + used + " КП. Снимите активность вместо удаления.");
        }
        int deleted = jdbc.update("delete from kp_drone_models where id=?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("Модель не найдена");
        }
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
        String pdf = kpHtmlPdfService.generate(proposal);
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
                lr.getBigDecimal("line_total"),
                readKitItems(lr.getString("kit_items"))
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
            String kitJson = null;
            if (l.lineType() == KpDtos.LineType.KIT && l.kitItems() != null && !l.kitItems().isEmpty()) {
                try {
                    kitJson = objectMapper.writeValueAsString(l.kitItems());
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to serialize kit items", ex);
                }
            }
            jdbc.update("""
                    insert into kp_proposal_lines(id, proposal_id, line_type, ref_id, sku, name, qty, unit_price, discount_pct, line_total, kit_items)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """, UUID.randomUUID(), proposalId, l.lineType().name(), l.refId(), l.sku(), l.name(), l.qty(), l.unitPrice(), discount, total, kitJson);
        }
    }

    private List<ProposalKitItemDto> readKitItems(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProposalKitItemDto>>() {});
        } catch (Exception ex) {
            return List.of();
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

    private Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal grand, BigDecimal nds) {}
}
