package com.kindred.emkcrm_project_backend.db.entities.warehouse;

import com.kindred.emkcrm_project_backend.db.entities.IlliquidAssets;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "illiquid_asset_history")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "asset")
public class IlliquidAssetHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private IlliquidAssets asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private IlliquidAssetHistoryOperationType operationType;

    @Column(name = "old_quantity", nullable = false)
    private Float oldQuantity;

    @Column(name = "new_quantity", nullable = false)
    private Float newQuantity;

    @Column(name = "quantity_delta", nullable = false)
    private Float quantityDelta;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "related_tender_id")
    private Long relatedTenderId;

    @Column(name = "changed_by_id", nullable = false)
    private Long changedById;

    @Column(name = "changed_by_username", nullable = false)
    private String changedByUsername;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
