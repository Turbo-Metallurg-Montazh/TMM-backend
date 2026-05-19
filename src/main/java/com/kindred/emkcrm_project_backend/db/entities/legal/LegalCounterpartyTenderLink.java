package com.kindred.emkcrm_project_backend.db.entities.legal;

import com.kindred.emkcrm_project_backend.db.entities.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "legal_counterparty_tender_link")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@ToString(exclude = "counterparty")
public class LegalCounterpartyTenderLink extends AuditableEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id", nullable = false)
    private LegalCounterparty counterparty;

    @Column(name = "tender_id")
    private String tenderId;

    @Column(name = "tender_name", length = 1024)
    private String tenderName;

    @Column(name = "tender_url", nullable = false, length = 2048)
    private String tenderUrl;

    @Column(name = "relation_type", length = 32)
    private String relationType;
}
