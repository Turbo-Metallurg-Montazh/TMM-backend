package com.kindred.emkcrm_project_backend.db.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "tender_filter")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@ToString
public class TenderFilter extends AuditableEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "text_values", columnDefinition = "TEXT[]")
    private String[] text;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "exclude_values", columnDefinition = "TEXT[]")
    private String[] exclude;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "categories", columnDefinition = "INTEGER[]")
    private Integer[] categories;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "include_inns", columnDefinition = "TEXT[]")
    private String[] includeInns;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "exclude_inns", columnDefinition = "TEXT[]")
    private String[] excludeInns;

    @Column(name = "date_time_from", nullable = false)
    private Instant dateTimeFrom;

    @Column(name = "date_time_to", nullable = false)
    private Instant dateTimeTo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "participants_inns", columnDefinition = "TEXT[]")
    private String[] participantsInns;

    @Column(name = "participants_state")
    private Integer participantsState;

    @Column(name = "enable_participants_from_documents")
    private Boolean enableParticipantsFromDocuments;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "region_ids", columnDefinition = "TEXT[]")
    private String[] regionIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "purchase_statuses", columnDefinition = "INTEGER[]")
    private Integer[] purchaseStatuses;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "laws", columnDefinition = "INTEGER[]")
    private Integer[] laws;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "procedures", columnDefinition = "INTEGER[]")
    private Integer[] procedures;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "electronic_places", columnDefinition = "INTEGER[]")
    private Integer[] electronicPlaces;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "category_ids", columnDefinition = "INTEGER[]")
    private Integer[] categoryIds;

    @Column(name = "strict_search")
    private Boolean strictSearch;

    @Column(name = "attachments")
    private Boolean attachments;

    @Column(name = "max_price_from")
    private Long maxPriceFrom;

    @Column(name = "max_price_to")
    private Long maxPriceTo;

    @Column(name = "max_price_none")
    private Boolean maxPriceNone;

    @Column(name = "advance_44")
    private Boolean advance44;

    @Column(name = "advance_223")
    private Boolean advance223;

    @Column(name = "non_advance")
    private Boolean nonAdvance;

    @Column(name = "smp")
    private Integer smp;

    @Column(name = "allow_foreign_currency")
    private Boolean allowForeignCurrency;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "application_deadline_from")
    private Instant applicationDeadlineFrom;

    @Column(name = "application_deadline_to")
    private Instant applicationDeadlineTo;
}
