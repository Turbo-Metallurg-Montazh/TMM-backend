package com.kindred.emkcrm_project_backend.db.entities.legal;

import com.kindred.emkcrm_project_backend.db.entities.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "legal_counterparty")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@ToString(exclude = {"checks", "incidents", "tenderLinks"})
public class LegalCounterparty extends AuditableEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "company_name", nullable = false, length = 512)
    private String companyName;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "inn", nullable = false, unique = true, length = 12)
    private String inn;

    @Column(name = "kpp", length = 9)
    private String kpp;

    @Column(name = "ogrn", length = 15)
    private String ogrn;

    @Enumerated(EnumType.STRING)
    @Column(name = "registry_type", nullable = false, length = 16)
    private CounterpartyRegistryType registryType;

    @Column(name = "general_risks")
    private String generalRisks;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private CounterpartyRiskLevel riskLevel = CounterpartyRiskLevel.UNKNOWN;

    @Column(name = "work_prohibited", nullable = false)
    private boolean workProhibited;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "legal_comment")
    private String legalComment;

    @Column(name = "created_by_username")
    private String createdByUsername;

    @Column(name = "updated_by_username")
    private String updatedByUsername;

    @OneToMany(mappedBy = "counterparty", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("checkedAt DESC, id DESC")
    private List<LegalCounterpartyCheck> checks = new ArrayList<>();

    @OneToMany(mappedBy = "counterparty", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("incidentDate DESC, id DESC")
    private List<LegalCounterpartyIncident> incidents = new ArrayList<>();

    @OneToMany(mappedBy = "counterparty", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LegalCounterpartyTenderLink> tenderLinks = new ArrayList<>();
}
