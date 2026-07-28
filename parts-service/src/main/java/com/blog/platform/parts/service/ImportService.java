package com.blog.platform.parts.service;

import com.blog.platform.parts.api.dto.ImportDtos.ColumnMapping;
import com.blog.platform.parts.api.dto.ImportDtos.ImportApplyRequest;
import com.blog.platform.parts.api.dto.ImportDtos.ImportApplyResponse;
import com.blog.platform.parts.api.dto.ImportDtos.ImportFormat;
import com.blog.platform.parts.api.dto.ImportDtos.ImportPreviewResponse;
import com.blog.platform.parts.api.dto.ImportDtos.PreviewStats;
import com.blog.platform.parts.api.dto.ImportDtos.RowIssue;
import com.blog.platform.parts.api.dto.ImportDtos.TargetField;
import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.domain.Drone;
import com.blog.platform.parts.domain.ExternalSource;
import com.blog.platform.parts.domain.Kit;
import com.blog.platform.parts.domain.KitItem;
import com.blog.platform.parts.domain.KitPriceMode;
import com.blog.platform.parts.domain.Part;
import com.blog.platform.parts.domain.PartCategory;
import com.blog.platform.parts.repository.DroneRepository;
import com.blog.platform.parts.repository.KitRepository;
import com.blog.platform.parts.repository.PartCategoryRepository;
import com.blog.platform.parts.repository.PartRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ImportService {

    private static final Set<String> SKU_ALIASES = Set.of(
            "sku", "артикул", "артикул запчасти", "код", "код товара", "article", "articul"
    );
    private static final Set<String> NAME_ALIASES = Set.of("name", "название", "наименование", "товар");
    private static final Set<String> PRICE_ALIASES = Set.of("price", "цена", "розничная цена", "saleprice", "sale_price");
    private static final Set<String> DESC_ALIASES = Set.of("description", "описание", "desc");
    private static final Set<String> DRONE_ALIASES = Set.of("drone", "дрон", "модель", "модель дрона");
    private static final Set<String> CATEGORY_ALIASES = Set.of("category", "категория", "группа", "group");
    private static final Set<String> KIT_ALIASES = Set.of("kit_sku", "kit", "артикул комплекта", "комплект");
    private static final Set<String> EXTERNAL_ALIASES = Set.of("external_id", "uid", "gbs_uid", "id");
    private static final Set<String> BARCODE_ALIASES = Set.of("barcode", "штрихкод", "штрих-код", "ean");

    private final PartRepository partRepository;
    private final DroneRepository droneRepository;
    private final PartCategoryRepository categoryRepository;
    private final KitRepository kitRepository;
    private final SlugService slugService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(MultipartFile file) {
        ParsedTable table = parseFile(file);
        List<ColumnMapping> mapping = suggestMapping(table.headers(), table.format());
        PreviewAnalysis analysis = analyze(table.rows(), mapping);
        return new ImportPreviewResponse(
                table.format(),
                table.headers(),
                mapping,
                table.rows().stream().limit(8).toList(),
                table.rows().size(),
                analysis.stats(),
                analysis.issues().stream().limit(30).toList()
        );
    }

    @Transactional
    public ImportApplyResponse apply(MultipartFile file, ImportApplyRequest request) {
        ParsedTable table = parseFile(file);
        List<ColumnMapping> mapping = request.mapping() == null || request.mapping().isEmpty()
                ? suggestMapping(table.headers(), table.format())
                : request.mapping();
        boolean createDrones = request.createMissingDrones();
        boolean createCategories = request.createMissingCategories();
        boolean attachKits = request.attachToKits();
        CatalogStatus status = parseStatus(request.defaultStatus());
        ExternalSource source = table.format() == ImportFormat.GBS_JSON ? ExternalSource.GBS : ExternalSource.IMPORT;

        int created = 0;
        int updated = 0;
        int skipped = 0;
        int kitsTouched = 0;
        List<RowIssue> errors = new ArrayList<>();
        int rowNumber = 1;

        for (Map<String, String> row : table.rows()) {
            rowNumber++;
            try {
                MappedRow mapped = mapRow(row, mapping);
                if (mapped.sku() == null || mapped.sku().isBlank()) {
                    if (mapped.barcode() != null && !mapped.barcode().isBlank()) {
                        mapped = mapped.withSku(mapped.barcode());
                    }
                }
                if (mapped.sku() == null || mapped.sku().isBlank() || mapped.name() == null || mapped.name().isBlank()) {
                    skipped++;
                    errors.add(new RowIssue(rowNumber, "Нужны артикул и название"));
                    continue;
                }
                if (mapped.price() == null) {
                    skipped++;
                    errors.add(new RowIssue(rowNumber, "Нужна цена"));
                    continue;
                }

                Optional<Part> existing = findExisting(mapped);
                Part part = existing.orElseGet(Part::new);
                boolean isNew = existing.isEmpty();
                part.setSku(mapped.sku().trim());
                part.setName(mapped.name().trim());
                part.setDescription(blankToNull(mapped.description()));
                part.setPrice(mapped.price());
                part.setCurrency("RUB");
                part.setExternalSource(source);
                if (mapped.externalId() != null && !mapped.externalId().isBlank()) {
                    part.setExternalId(mapped.externalId().trim());
                }
                if (isNew) {
                    part.setStatus(status);
                }
                part.setDrone(resolveDrone(mapped.drone(), createDrones));
                part.setCategory(resolveCategory(mapped.category(), createCategories));
                part = partRepository.save(part);

                if (attachKits && mapped.kitSku() != null && !mapped.kitSku().isBlank()) {
                    if (attachPartToKit(part, mapped.kitSku().trim())) {
                        kitsTouched++;
                    }
                }

                if (isNew) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception ex) {
                skipped++;
                errors.add(new RowIssue(rowNumber, ex.getMessage() == null ? "Ошибка строки" : ex.getMessage()));
            }
        }

        return new ImportApplyResponse(created, updated, skipped, kitsTouched, errors.stream().limit(50).toList());
    }

    public byte[] csvTemplate() {
        String content = "sku,name,price,drone,category,kit_sku,description,external_id\n"
                + "PART-001,Пример фильтра,1990,DJI Mavic 3,Фильтры,KIT-001,Описание,ext-1\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private ParsedTable parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (filename.endsWith(".csv") || looksLikeCsv(bytes)) {
                return parseCsv(bytes);
            }
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                return parseExcel(bytes);
            }
            if (filename.endsWith(".json") || looksLikeJson(bytes)) {
                return parseJson(bytes);
            }
            throw new IllegalArgumentException("Поддерживаются CSV, Excel (.xlsx) и JSON (в т.ч. GBS.Market)");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Не удалось прочитать файл: " + ex.getMessage(), ex);
        }
    }

    private ParsedTable parseCsv(byte[] bytes) throws Exception {
        String sample = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.UTF_8);
        char delimiter = sample.contains(";") && countChar(sample, ';') >= countChar(sample, ',') ? ';' : ',';
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setDelimiter(delimiter)
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            if (headers.isEmpty()) {
                throw new IllegalArgumentException("В CSV нет заголовков");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) {
                    row.put(header, safe(record.get(header)));
                }
                if (row.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
                    rows.add(row);
                }
            }
            return new ParsedTable(ImportFormat.CSV, headers, rows);
        }
    }

    private ParsedTable parseExcel(byte[] bytes) throws Exception {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("В Excel нет листов");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("В Excel нет заголовков");
            }
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String value = formatter.formatCellValue(headerRow.getCell(i)).trim();
                if (!value.isBlank()) {
                    headers.add(value);
                }
            }
            if (headers.isEmpty()) {
                throw new IllegalArgumentException("В Excel нет заголовков");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> mapped = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    mapped.put(headers.get(c), value);
                    if (!value.isBlank()) {
                        any = true;
                    }
                }
                if (any) {
                    rows.add(mapped);
                }
            }
            return new ParsedTable(ImportFormat.XLSX, headers, rows);
        }
    }

    private ParsedTable parseJson(byte[] bytes) throws Exception {
        JsonNode root = objectMapper.readTree(bytes);
        JsonNode data = root;
        if (root.has("Data")) {
            data = root.get("Data");
        } else if (root.has("data")) {
            data = root.get("data");
        }
        if (data != null && data.isObject() && data.has("goods")) {
            data = data.get("goods");
        }
        if (data == null || !data.isArray()) {
            throw new IllegalArgumentException("JSON должен содержать массив товаров (или GBS.Market Data[])");
        }

        boolean gbsLike = false;
        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode node : data) {
            if (!node.isObject()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            if (node.has("Uid") || node.has("Stocks") || node.has("Group") || node.has("Properties")) {
                gbsLike = true;
                row.put("Uid", text(node, "Uid"));
                row.put("Name", text(node, "Name"));
                row.put("Barcode", text(node, "Barcode"));
                JsonNode group = node.get("Group");
                row.put("Group", group != null && group.has("Name") ? group.get("Name").asText("") : "");
                row.put("Price", extractGbsPrice(node));
                row.put("sku", extractGbsProperty(node, Set.of("артикул запчасти", "артикул", "sku", "код товара")));
                row.put("kit_sku", extractGbsProperty(node, Set.of("артикул комплекта", "комплект", "kit")));
                row.put("drone", extractGbsProperty(node, Set.of("дрон", "drone", "модель")));
                row.put("description", extractGbsProperty(node, Set.of("описание", "description")));
            } else {
                node.fields().forEachRemaining(entry -> row.put(entry.getKey(), entry.getValue().asText("")));
            }
            if (row.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
                rows.add(row);
            }
        }
        List<String> headers = rows.isEmpty()
                ? List.of("sku", "name", "price")
                : new ArrayList<>(rows.get(0).keySet());
        return new ParsedTable(gbsLike ? ImportFormat.GBS_JSON : ImportFormat.JSON, headers, rows);
    }

    private String extractGbsPrice(JsonNode node) {
        JsonNode stocks = node.get("Stocks");
        if (stocks != null && stocks.isArray() && !stocks.isEmpty()) {
            JsonNode first = stocks.get(0);
            if (first.has("Price")) {
                return first.get("Price").asText("0");
            }
        }
        if (node.has("Price")) {
            return node.get("Price").asText("0");
        }
        return "0";
    }

    private String extractGbsProperty(JsonNode node, Set<String> names) {
        JsonNode props = node.get("Properties");
        if (props == null || !props.isArray()) {
            return "";
        }
        for (JsonNode prop : props) {
            String typeName = prop.has("TypeName") ? prop.get("TypeName").asText("").trim().toLowerCase(Locale.ROOT) : "";
            if (names.contains(typeName)) {
                return prop.has("Value") ? prop.get("Value").asText("") : "";
            }
        }
        return "";
    }

    private List<ColumnMapping> suggestMapping(List<String> headers, ImportFormat format) {
        List<ColumnMapping> mapping = new ArrayList<>();
        for (String header : headers) {
            mapping.add(new ColumnMapping(header, guessField(header, format)));
        }
        return mapping;
    }

    private TargetField guessField(String header, ImportFormat format) {
        String key = normalizeHeader(header);
        if (format == ImportFormat.GBS_JSON) {
            return switch (key) {
                case "uid" -> TargetField.EXTERNAL_ID;
                case "name" -> TargetField.NAME;
                case "barcode" -> TargetField.BARCODE;
                case "group" -> TargetField.CATEGORY;
                case "price" -> TargetField.PRICE;
                case "sku" -> TargetField.SKU;
                case "kit_sku" -> TargetField.KIT_SKU;
                case "drone" -> TargetField.DRONE;
                case "description" -> TargetField.DESCRIPTION;
                default -> TargetField.SKIP;
            };
        }
        if (SKU_ALIASES.contains(key)) return TargetField.SKU;
        if (NAME_ALIASES.contains(key)) return TargetField.NAME;
        if (PRICE_ALIASES.contains(key)) return TargetField.PRICE;
        if (DESC_ALIASES.contains(key)) return TargetField.DESCRIPTION;
        if (DRONE_ALIASES.contains(key)) return TargetField.DRONE;
        if (CATEGORY_ALIASES.contains(key)) return TargetField.CATEGORY;
        if (KIT_ALIASES.contains(key)) return TargetField.KIT_SKU;
        if (EXTERNAL_ALIASES.contains(key)) return TargetField.EXTERNAL_ID;
        if (BARCODE_ALIASES.contains(key)) return TargetField.BARCODE;
        return TargetField.SKIP;
    }

    private PreviewAnalysis analyze(List<Map<String, String>> rows, List<ColumnMapping> mapping) {
        int valid = 0;
        int toCreate = 0;
        int toUpdate = 0;
        int invalid = 0;
        List<RowIssue> issues = new ArrayList<>();
        int rowNumber = 1;
        for (Map<String, String> row : rows) {
            rowNumber++;
            MappedRow mapped = mapRow(row, mapping);
            if ((mapped.sku() == null || mapped.sku().isBlank()) && mapped.barcode() != null) {
                mapped = mapped.withSku(mapped.barcode());
            }
            if (mapped.sku() == null || mapped.sku().isBlank() || mapped.name() == null || mapped.name().isBlank() || mapped.price() == null) {
                invalid++;
                issues.add(new RowIssue(rowNumber, "Нужны артикул, название и цена"));
                continue;
            }
            valid++;
            if (findExisting(mapped).isPresent()) {
                toUpdate++;
            } else {
                toCreate++;
            }
        }
        return new PreviewAnalysis(new PreviewStats(valid, toCreate, toUpdate, invalid), issues);
    }

    private MappedRow mapRow(Map<String, String> row, List<ColumnMapping> mapping) {
        String sku = null;
        String name = null;
        String description = null;
        String drone = null;
        String category = null;
        String kitSku = null;
        String externalId = null;
        String barcode = null;
        BigDecimal price = null;

        for (ColumnMapping column : mapping) {
            if (column == null || column.targetField() == null || column.targetField() == TargetField.SKIP) {
                continue;
            }
            String value = row.get(column.sourceColumn());
            if (value == null || value.isBlank()) {
                continue;
            }
            switch (column.targetField()) {
                case SKU -> sku = value.trim();
                case NAME -> name = value.trim();
                case PRICE -> price = parsePrice(value);
                case DESCRIPTION -> description = value.trim();
                case DRONE -> drone = value.trim();
                case CATEGORY -> category = value.trim();
                case KIT_SKU -> kitSku = value.trim();
                case EXTERNAL_ID -> externalId = value.trim();
                case BARCODE -> barcode = value.trim();
                default -> {
                }
            }
        }
        return new MappedRow(sku, name, price, description, drone, category, kitSku, externalId, barcode);
    }

    private Optional<Part> findExisting(MappedRow mapped) {
        if (mapped.externalId() != null && !mapped.externalId().isBlank()) {
            Optional<Part> byExternal = partRepository.findByExternalId(mapped.externalId().trim());
            if (byExternal.isPresent()) {
                return byExternal;
            }
        }
        if (mapped.sku() != null && !mapped.sku().isBlank()) {
            return partRepository.findBySku(mapped.sku().trim());
        }
        return Optional.empty();
    }

    private Drone resolveDrone(String name, boolean createMissing) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Optional<Drone> existing = droneRepository.findByNameIgnoreCase(name.trim());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!createMissing) {
            return null;
        }
        Drone drone = new Drone();
        drone.setName(name.trim());
        drone.setSlug(slugService.uniqueSlug(name, droneRepository::existsBySlug));
        drone.setStatus(CatalogStatus.DRAFT);
        drone.setSortOrder(0);
        return droneRepository.save(drone);
    }

    private PartCategory resolveCategory(String name, boolean createMissing) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Optional<PartCategory> existing = categoryRepository.findByNameIgnoreCase(name.trim());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!createMissing) {
            return null;
        }
        PartCategory category = new PartCategory();
        category.setName(name.trim());
        category.setSlug(slugService.uniqueSlug(name, categoryRepository::existsBySlug));
        category.setSortOrder(0);
        return categoryRepository.save(category);
    }

    private boolean attachPartToKit(Part part, String kitSku) {
        Kit kit = kitRepository.findBySku(kitSku).orElseGet(() -> {
            Kit created = new Kit();
            created.setSku(kitSku);
            created.setName("Комплект " + kitSku);
            created.setPrice(BigDecimal.ZERO);
            created.setCurrency("RUB");
            created.setPriceMode(KitPriceMode.SUM);
            created.setStatus(CatalogStatus.DRAFT);
            created.setSortOrder(0);
            created.setItems(new ArrayList<>());
            return kitRepository.save(created);
        });
        boolean already = kit.getItems().stream().anyMatch(item -> part.getId().equals(item.getPartId()));
        if (already) {
            return false;
        }
        KitItem item = new KitItem();
        item.setPart(part);
        item.setQty(1);
        kit.getItems().add(item);
        if (kit.getPriceMode() == KitPriceMode.SUM) {
            BigDecimal sum = kit.getItems().stream()
                    .map(i -> i.getPart().getPrice().multiply(BigDecimal.valueOf(i.getQty())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            kit.setPrice(sum);
        }
        kitRepository.save(kit);
        return true;
    }

    private BigDecimal parsePrice(String value) {
        try {
            String normalized = value.trim()
                    .replace(" ", "")
                    .replace("\u00A0", "")
                    .replace("₽", "")
                    .replace("руб.", "")
                    .replace("руб", "")
                    .replace(",", ".");
            if (normalized.isBlank()) {
                return null;
            }
            BigDecimal price = new BigDecimal(normalized);
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            return price;
        } catch (Exception ex) {
            return null;
        }
    }

    private CatalogStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return CatalogStatus.DRAFT;
        }
        try {
            return CatalogStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return CatalogStatus.DRAFT;
        }
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
    }

    private String text(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText("") : "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean looksLikeCsv(byte[] bytes) {
        String sample = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.UTF_8);
        return sample.contains(",") || sample.contains(";");
    }

    private boolean looksLikeJson(byte[] bytes) {
        for (byte b : bytes) {
            char c = (char) b;
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '{' || c == '[';
        }
        return false;
    }

    private int countChar(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private record ParsedTable(ImportFormat format, List<String> headers, List<Map<String, String>> rows) {
    }

    private record PreviewAnalysis(PreviewStats stats, List<RowIssue> issues) {
    }

    private record MappedRow(
            String sku,
            String name,
            BigDecimal price,
            String description,
            String drone,
            String category,
            String kitSku,
            String externalId,
            String barcode
    ) {
        MappedRow withSku(String newSku) {
            return new MappedRow(newSku, name, price, description, drone, category, kitSku, externalId, barcode);
        }
    }
}
