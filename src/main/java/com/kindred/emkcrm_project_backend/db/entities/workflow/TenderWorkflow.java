package com.kindred.emkcrm_project_backend.db.entities.workflow;

import com.kindred.emkcrm_project_backend.db.entities.AuditableEntity;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.entities.tenderEntity.Tender;
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
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
        name = "tender_workflow",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tender_workflow_purchase_tender", columnNames = "purchase_tender_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@ToString(exclude = {"purchaseTender", "responsibleManager", "supplyUser", "lawyerUser", "approvedBy", "createdBy", "updatedBy"})
public class TenderWorkflow extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_tender_id", nullable = false)
    private Tender purchaseTender;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private TenderWorkflowStatus status = TenderWorkflowStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private TenderWorkflowPriority priority = TenderWorkflowPriority.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_manager_id")
    private User responsibleManager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supply_user_id")
    private User supplyUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_user_id")
    private User lawyerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "lost_reason", columnDefinition = "TEXT")
    private String lostReason;

    @Column(name = "result_comment", columnDefinition = "TEXT")
    private String resultComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;
}
