package com.blog.platform.logging.service;

import com.blog.platform.logging.api.dto.LoggingDtos.LogEventInput;
import com.blog.platform.logging.api.dto.LoggingDtos.LogEventView;
import com.blog.platform.logging.api.dto.LoggingDtos.LogIngestResponse;
import com.blog.platform.logging.api.dto.LoggingDtos.LogQueryResponse;
import com.blog.platform.logging.api.dto.LoggingDtos.LogStatsResponse;
import com.blog.platform.logging.domain.LogEventRecord;
import com.blog.platform.logging.repository.LogEventRepository;
import com.blog.platform.logging.util.Hashing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoggingService {

    private static final List<String> HIGH_PRIORITY_LEVELS = List.of("ERROR", "WARN", "SECURITY", "AUDIT");

    private final LogEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Deque<LogEventRecord> queue = new ArrayDeque<>();

    @Value("${logging-service.ingest.queue-capacity:500}")
    private int queueCapacity;

    @Value("${logging-service.ingest.flush-size:200}")
    private int flushSize;

    @Value("${logging-service.retention.days:14}")
    private int retentionDays;

    @Value("${logging-service.retention.max-rows:100000}")
    private int maxRows;

    @Value("${logging-service.retention.max-db-mb:256}")
    private int maxDbMb;

    @Value("${logging-service.db-path:/data/logs/logging.db}")
    private String dbPath;

    @PostConstruct
    void ensureDbDir() {
        try {
            Path path = Path.of(dbPath).toAbsolutePath().getParent();
            if (path != null) {
                Files.createDirectories(path);
            }
        } catch (Exception ignored) {
            // no-op
        }
    }

    public LogIngestResponse ingest(List<LogEventInput> events) {
        int accepted = 0;
        int dropped = 0;
        for (LogEventInput input : events) {
            LogEventRecord record = normalize(input);
            if (record == null) {
                dropped++;
                continue;
            }
            if (enqueue(record)) {
                accepted++;
            } else {
                dropped++;
            }
        }
        return new LogIngestResponse(accepted, dropped);
    }

    public LogQueryResponse query(
            Instant from,
            Instant to,
            String level,
            String category,
            String service,
            String q,
            int page,
            int size
    ) {
        Instant effectiveFrom = from == null ? Instant.now().minus(24, ChronoUnit.HOURS) : from;
        Instant effectiveTo = to == null ? Instant.now() : to;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        long total = repository.countByFilters(effectiveFrom, effectiveTo, level, category, service, q);
        List<LogEventView> items = repository.findByFilters(effectiveFrom, effectiveTo, level, category, service, q, safePage, safeSize);
        return new LogQueryResponse(items, total, safePage, safeSize);
    }

    public LogStatsResponse stats(Instant from, Instant to) {
        Instant effectiveFrom = from == null ? Instant.now().minus(24, ChronoUnit.HOURS) : from;
        Instant effectiveTo = to == null ? Instant.now() : to;
        return new LogStatsResponse(
                repository.countByLevel(effectiveFrom, effectiveTo),
                repository.countByCategory(effectiveFrom, effectiveTo)
        );
    }

    @Scheduled(fixedDelayString = "${logging-service.ingest.flush-ms:500}")
    public void flushQueue() {
        List<LogEventRecord> batch = new ArrayList<>(flushSize);
        synchronized (queue) {
            while (!queue.isEmpty() && batch.size() < flushSize) {
                batch.add(queue.removeFirst());
            }
        }
        if (!batch.isEmpty()) {
            repository.upsertBatch(batch);
        }
    }

    @Scheduled(fixedDelayString = "${logging-service.retention.run-ms:300000}")
    public void enforceRetention() {
        repository.deleteOlderThan(Instant.now().minus(retentionDays, ChronoUnit.DAYS));

        long totalRows = repository.countAll();
        if (totalRows > maxRows) {
            int overflow = (int) Math.min(Integer.MAX_VALUE, totalRows - maxRows);
            repository.deleteOldest(overflow);
        }

        try {
            long bytes = Files.exists(Path.of(dbPath)) ? Files.size(Path.of(dbPath)) : 0L;
            long limit = (long) maxDbMb * 1024 * 1024;
            if (bytes > limit) {
                repository.vacuum();
            }
        } catch (Exception ignored) {
            // no-op
        }
    }

    private LogEventRecord normalize(LogEventInput input) {
        String level = normalizeLevel(input.level());
        String category = normalizeCategory(input.category());
        if (!shouldStore(level, category)) {
            return null;
        }
        String message = input.message().trim();
        String service = input.service().trim();
        String detailsJson = serializeDetails(input.details());
        String fingerprint = Hashing.sha256Hex(service + "|" + category + "|" + level + "|" + message);
        return new LogEventRecord(
                service,
                category,
                level,
                message,
                detailsJson,
                blankToNull(input.actorId()),
                blankToNull(input.actorUsername()),
                blankToNull(input.requestId()),
                input.occurredAt(),
                fingerprint
        );
    }

    private boolean enqueue(LogEventRecord record) {
        synchronized (queue) {
            if (queue.size() >= queueCapacity) {
                if (!isHighPriority(record.level()) && !"SECURITY".equals(record.category())) {
                    return false;
                }
                if (!dropLowPriority()) {
                    queue.removeFirst();
                }
            }
            queue.addLast(record);
            return true;
        }
    }

    private boolean dropLowPriority() {
        for (LogEventRecord record : queue) {
            if (!isHighPriority(record.level()) && !"SECURITY".equals(record.category())) {
                queue.remove(record);
                return true;
            }
        }
        return false;
    }

    private boolean shouldStore(String level, String category) {
        if ("DEBUG".equals(level)) {
            return false;
        }
        if ("INFO".equals(level)) {
            return List.of("AUTH", "SECURITY", "USERS", "CONTENT", "CATALOG", "MEDIA", "KP", "SYSTEM").contains(category);
        }
        return true;
    }

    private boolean isHighPriority(String level) {
        return HIGH_PRIORITY_LEVELS.contains(level);
    }

    private String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return "INFO";
        }
        return level.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "SYSTEM";
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String serializeDetails(Object details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"unserializable\"}";
        }
    }
}
