package com.blog.platform.logging.repository;

import com.blog.platform.logging.api.dto.LoggingDtos.LogEventView;
import com.blog.platform.logging.domain.LogEventRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LogEventRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<LogEventView> rowMapper = this::mapRow;

    public void upsertBatch(List<LogEventRecord> records) {
        String sql = """
                INSERT INTO log_events (
                    created_at, last_seen_at, service, category, level, message,
                    details_json, actor_id, actor_username, request_id, fingerprint, count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON CONFLICT(fingerprint) DO UPDATE SET
                    last_seen_at = excluded.last_seen_at,
                    details_json = excluded.details_json,
                    actor_id = COALESCE(excluded.actor_id, log_events.actor_id),
                    actor_username = COALESCE(excluded.actor_username, log_events.actor_username),
                    request_id = COALESCE(excluded.request_id, log_events.request_id),
                    count = log_events.count + 1
                """;

        jdbcTemplate.batchUpdate(sql, records, records.size(), (ps, record) -> {
            Instant createdAt = record.occurredAt() == null ? Instant.now() : record.occurredAt().truncatedTo(ChronoUnit.SECONDS);
            Instant lastSeenAt = createdAt;
            ps.setString(1, createdAt.toString());
            ps.setString(2, lastSeenAt.toString());
            ps.setString(3, record.service());
            ps.setString(4, record.category());
            ps.setString(5, record.level());
            ps.setString(6, record.message());
            ps.setString(7, record.detailsJson());
            ps.setString(8, record.actorId());
            ps.setString(9, record.actorUsername());
            ps.setString(10, record.requestId());
            ps.setString(11, record.fingerprint());
        });
    }

    public long countByFilters(Instant from, Instant to, String level, String category, String service, String q) {
        SqlParts parts = filters(from, to, level, category, service, q);
        String sql = "SELECT COUNT(*) FROM log_events WHERE " + String.join(" AND ", parts.clauses);
        Long result = jdbcTemplate.queryForObject(sql, Long.class, parts.args.toArray());
        return result == null ? 0L : result;
    }

    public List<LogEventView> findByFilters(
            Instant from,
            Instant to,
            String level,
            String category,
            String service,
            String q,
            int page,
            int size
    ) {
        SqlParts parts = filters(from, to, level, category, service, q);
        String sql = "SELECT id, created_at, last_seen_at, service, category, level, message, details_json, actor_id, actor_username, request_id, count " +
                "FROM log_events WHERE " + String.join(" AND ", parts.clauses) + " " +
                "ORDER BY last_seen_at DESC LIMIT ? OFFSET ?";
        parts.args.add(size);
        parts.args.add(page * size);
        return jdbcTemplate.query(sql, rowMapper, parts.args.toArray());
    }

    public Map<String, Long> countByLevel(Instant from, Instant to) {
        return groupCount("level", from, to);
    }

    public Map<String, Long> countByCategory(Instant from, Instant to) {
        return groupCount("category", from, to);
    }

    public void deleteOlderThan(Instant cutoff) {
        jdbcTemplate.update("DELETE FROM log_events WHERE last_seen_at < ?", cutoff.toString());
    }

    public long countAll() {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM log_events", Long.class);
        return result == null ? 0L : result;
    }

    public int deleteOldest(int rowsToDelete) {
        return jdbcTemplate.update("DELETE FROM log_events WHERE id IN (SELECT id FROM log_events ORDER BY last_seen_at ASC LIMIT ?)", rowsToDelete);
    }

    public void vacuum() {
        jdbcTemplate.execute("VACUUM");
    }

    private Map<String, Long> groupCount(String column, Instant from, Instant to) {
        String sql = "SELECT " + column + ", SUM(count) AS total FROM log_events WHERE last_seen_at >= ? AND last_seen_at <= ? GROUP BY " + column;
        Map<String, Long> result = new LinkedHashMap<>();
        var rows = jdbcTemplate.queryForList(sql, from.toString(), to.toString());
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get(column)), ((Number) row.get("total")).longValue());
        }
        return result;
    }

    private SqlParts filters(Instant from, Instant to, String level, String category, String service, String q) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        clauses.add("last_seen_at >= ?");
        args.add(from.toString());
        clauses.add("last_seen_at <= ?");
        args.add(to.toString());

        if (level != null && !level.isBlank()) {
            clauses.add("level = ?");
            args.add(level.toUpperCase());
        }
        if (category != null && !category.isBlank()) {
            clauses.add("category = ?");
            args.add(category.toUpperCase());
        }
        if (service != null && !service.isBlank()) {
            clauses.add("service = ?");
            args.add(service);
        }
        if (q != null && !q.isBlank()) {
            clauses.add("(message LIKE ? OR actor_username LIKE ? OR request_id LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return new SqlParts(clauses, args);
    }

    private LogEventView mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LogEventView(
                rs.getLong("id"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("last_seen_at")),
                rs.getString("service"),
                rs.getString("category"),
                rs.getString("level"),
                rs.getString("message"),
                rs.getString("details_json"),
                rs.getString("actor_id"),
                rs.getString("actor_username"),
                rs.getString("request_id"),
                rs.getLong("count")
        );
    }

    private record SqlParts(List<String> clauses, List<Object> args) {
    }
}


