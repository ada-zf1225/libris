package com.libris.domain.circulation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "circulation_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CirculationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "copy_id")
    private Long copyId;

    @Column(name = "reader_id")
    private Long readerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CirculationLog(Long operatorId, String action, Long copyId, Long readerId, Map<String, Object> detail) {
        this.operatorId = operatorId;
        this.action = action;
        this.copyId = copyId;
        this.readerId = readerId;
        this.detail = detail;
    }
}
