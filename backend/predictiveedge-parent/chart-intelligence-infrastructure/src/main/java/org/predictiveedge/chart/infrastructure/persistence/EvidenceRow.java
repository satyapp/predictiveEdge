package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Shared columns of the eleven per-domain evidence tables. Each domain maps its own table. */
@MappedSuperclass
public abstract class EvidenceRow {
    @EmbeddedId
    private EvidenceKey key;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private String payload;

    @Column(name = "detail")
    private String detail;

    protected EvidenceRow() { }

    protected EvidenceRow(EvidenceKey key, String role, String status, Instant fetchedAt, String payload,
            String detail) {
        this.key = key;
        this.role = role;
        this.status = status;
        this.fetchedAt = fetchedAt;
        this.payload = payload;
        this.detail = detail;
    }
}
