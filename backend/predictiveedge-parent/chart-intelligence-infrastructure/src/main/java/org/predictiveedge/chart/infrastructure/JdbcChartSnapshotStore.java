package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.chart.application.ChartSnapshotPublicationPort;
import org.predictiveedge.chart.application.ChartSnapshotQueryPort;
import org.predictiveedge.chart.domain.ChartSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;

/** Append-only PostgreSQL store and causal reader for immutable chart snapshots. */
public final class JdbcChartSnapshotStore implements ChartSnapshotPublicationPort, ChartSnapshotQueryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcChartSnapshotStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
    }

    @Override
    public boolean append(UUID userId, ChartSnapshot snapshot) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(snapshot, "Chart snapshot is required");
        int changed = jdbc.update("""
                insert into chart_intelligence.chart_snapshot (
                  user_id,snapshot_id,venue,instrument_id,analysis_cutoff,knowledge_cutoff,
                  available_at,valid_until,input_manifest_hash,snapshot_json)
                values (?,?,?,?,?,?,?,?,?,?::jsonb)
                on conflict do nothing
                """, userId, snapshot.snapshotId(), snapshot.venue(), snapshot.instrumentId(),
                Timestamp.from(snapshot.analysisCutoff()), Timestamp.from(snapshot.knowledgeCutoff()),
                Timestamp.from(snapshot.availableAt()), Timestamp.from(snapshot.validUntil()),
                snapshot.inputManifestHash(), serialize(snapshot));
        return changed == 1;
    }

    @Override
    public Optional<ChartSnapshot> findLatest(
            UUID userId, String venue, String instrumentId, Instant cutoff) {
        Objects.requireNonNull(userId, "User id is required");
        venue = required(venue, "Venue").toUpperCase();
        instrumentId = required(instrumentId, "Instrument id").toUpperCase();
        Objects.requireNonNull(cutoff, "Chart cutoff is required");
        return jdbc.query("""
                select snapshot_json::text
                from chart_intelligence.chart_snapshot
                where user_id=? and venue=? and instrument_id=?
                  and analysis_cutoff<=? and knowledge_cutoff<=? and available_at<=?
                order by analysis_cutoff desc,knowledge_cutoff desc,available_at desc
                limit 1
                """, this::mapSnapshot, userId, venue, instrumentId, Timestamp.from(cutoff),
                Timestamp.from(cutoff), Timestamp.from(cutoff)).stream().findFirst();
    }

    private ChartSnapshot mapSnapshot(ResultSet result, int rowNumber) throws SQLException {
        try {
            return json.readValue(result.getString("snapshot_json"), ChartSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored chart snapshot is invalid", exception);
        }
    }

    private String serialize(ChartSnapshot snapshot) {
        try {
            return json.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Chart snapshot cannot be serialized", exception);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
