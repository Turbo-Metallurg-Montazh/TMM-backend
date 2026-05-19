package com.kindred.emkcrm_project_backend.services;

import com.kindred.emkcrm_project_backend.db.entities.legal.CounterpartyRegistryType;
import com.kindred.emkcrm_project_backend.db.entities.legal.CounterpartyRiskLevel;
import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterparty;
import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterpartyCheck;
import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterpartyIncident;
import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterpartyTenderLink;
import com.kindred.emkcrm_project_backend.db.repositories.LegalCounterpartyCheckRepository;
import com.kindred.emkcrm_project_backend.db.repositories.LegalCounterpartyIncidentRepository;
import com.kindred.emkcrm_project_backend.db.repositories.LegalCounterpartyRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.exception.NotFoundException;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyAssessmentResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyCheckCreateRequest;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyCheckResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyDetailsResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyIncidentCreateRequest;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyIncidentResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyPageResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartySummaryResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyTenderLinkRequest;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyTenderLinkResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyUpsertRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class LegalCounterpartyService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final LegalCounterpartyRepository counterpartyRepository;
    private final LegalCounterpartyCheckRepository checkRepository;
    private final LegalCounterpartyIncidentRepository incidentRepository;

    public LegalCounterpartyService(
            LegalCounterpartyRepository counterpartyRepository,
            LegalCounterpartyCheckRepository checkRepository,
            LegalCounterpartyIncidentRepository incidentRepository
    ) {
        this.counterpartyRepository = counterpartyRepository;
        this.checkRepository = checkRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.READ') or hasAuthority('CONTRACTOR.VIEW_REPORTS')")
    public LegalCounterpartyPageResponse search(
            String query,
            String registryType,
            String riskLevel,
            Boolean workProhibited,
            Integer page,
            Integer size
    ) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        CounterpartyRegistryType parsedRegistryType = parseOptionalRegistryType(registryType);
        CounterpartyRiskLevel parsedRiskLevel = parseOptionalRiskLevel(riskLevel, "riskLevel");

        Page<LegalCounterparty> result = counterpartyRepository.findAll((root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query != null && !query.isBlank()) {
                String searchValue = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("companyName")), searchValue),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("shortName")), searchValue),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("inn")), searchValue),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("kpp")), searchValue),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("ogrn")), searchValue)
                ));
            }

            if (parsedRegistryType != null) {
                if (parsedRegistryType == CounterpartyRegistryType.BOTH) {
                    predicates.add(criteriaBuilder.equal(root.get("registryType"), parsedRegistryType));
                } else {
                    predicates.add(root.get("registryType").in(parsedRegistryType, CounterpartyRegistryType.BOTH));
                }
            }

            if (parsedRiskLevel != null) {
                predicates.add(criteriaBuilder.equal(root.get("riskLevel"), parsedRiskLevel));
            }

            if (workProhibited != null) {
                predicates.add(criteriaBuilder.equal(root.get("workProhibited"), workProhibited));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "updatedAt")));

        LegalCounterpartyPageResponse response = new LegalCounterpartyPageResponse();
        response.setItems(result.getContent().stream().map(this::toSummaryResponse).toList());
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        return response;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.WRITE') or hasAuthority('CONTRACTOR.CHECK_RELIABILITY')")
    public LegalCounterpartyDetailsResponse create(LegalCounterpartyUpsertRequest request) {
        validateUpsertRequest(request);
        String inn = normalizeRequired(request.getInn(), "inn");
        if (counterpartyRepository.existsByInn(inn)) {
            throw new ConflictException("Контрагент с ИНН " + inn + " уже существует");
        }

        LegalCounterparty counterparty = new LegalCounterparty();
        counterparty.setCreatedByUsername(currentUsername());
        applyEditableFields(counterparty, request, inn);
        return toDetailsResponse(counterpartyRepository.save(counterparty));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.READ') or hasAuthority('CONTRACTOR.VIEW_REPORTS')")
    public LegalCounterpartyDetailsResponse getById(Long counterpartyId) {
        return toDetailsResponse(findByIdWithDetails(counterpartyId));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.WRITE') or hasAuthority('CONTRACTOR.CHECK_RELIABILITY')")
    public LegalCounterpartyDetailsResponse update(Long counterpartyId, LegalCounterpartyUpsertRequest request) {
        validateUpsertRequest(request);
        LegalCounterparty counterparty = findByIdWithDetails(counterpartyId);
        String inn = normalizeRequired(request.getInn(), "inn");
        if (counterpartyRepository.existsByInnAndIdNot(inn, counterparty.getId())) {
            throw new ConflictException("Контрагент с ИНН " + inn + " уже существует");
        }

        applyEditableFields(counterparty, request, inn);
        return toDetailsResponse(counterpartyRepository.save(counterparty));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.WRITE')")
    public String delete(Long counterpartyId) {
        LegalCounterparty counterparty = findById(counterpartyId);
        counterpartyRepository.delete(counterparty);
        return "Контрагент удален из юридического реестра";
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.WRITE') or hasAuthority('CONTRACTOR.CHECK_RELIABILITY')")
    public LegalCounterpartyCheckResponse addCheck(Long counterpartyId, LegalCounterpartyCheckCreateRequest request) {
        validateCheckRequest(request);
        LegalCounterparty counterparty = findById(counterpartyId);

        LegalCounterpartyCheck check = new LegalCounterpartyCheck();
        check.setCounterparty(counterparty);
        check.setCheckedAt(toLocalDateTime(request.getCheckedAt()));
        check.setOverallScore(request.getOverallScore());
        check.setRiskLevel(parseRequiredRiskLevel(request.getRiskLevel(), "riskLevel"));
        check.setWorkProhibited(Boolean.TRUE.equals(request.getWorkProhibited()));
        check.setRisks(trimToNull(request.getRisks()));
        check.setComment(trimToNull(request.getComment()));
        check.setCheckedByUsername(currentUsername());

        counterparty.setOverallScore(check.getOverallScore());
        counterparty.setRiskLevel(check.getRiskLevel());
        counterparty.setWorkProhibited(check.isWorkProhibited());
        counterparty.setLastCheckedAt(check.getCheckedAt());
        if (check.getRisks() != null) {
            counterparty.setGeneralRisks(check.getRisks());
        }
        counterparty.setUpdatedByUsername(check.getCheckedByUsername());

        counterpartyRepository.save(counterparty);
        return toCheckResponse(checkRepository.save(check));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.READ') or hasAuthority('CONTRACTOR.VIEW_REPORTS')")
    public List<LegalCounterpartyCheckResponse> listChecks(Long counterpartyId) {
        ensureExists(counterpartyId);
        return checkRepository.findAllByCounterpartyIdOrderByCheckedAtDescIdDesc(counterpartyId).stream()
                .map(this::toCheckResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.WRITE') or hasAuthority('CONTRACTOR.CHECK_RELIABILITY')")
    public LegalCounterpartyIncidentResponse addIncident(Long counterpartyId, LegalCounterpartyIncidentCreateRequest request) {
        validateIncidentRequest(request);
        LegalCounterparty counterparty = findById(counterpartyId);

        LegalCounterpartyIncident incident = new LegalCounterpartyIncident();
        incident.setCounterparty(counterparty);
        incident.setIncidentDate(request.getIncidentDate());
        incident.setTitle(normalizeRequired(request.getTitle(), "title"));
        incident.setDescription(trimToNull(request.getDescription()));
        incident.setImpactLevel(parseOptionalImpactLevelOrDefault(request.getImpactLevel()));
        incident.setCreatedByUsername(currentUsername());

        return toIncidentResponse(incidentRepository.save(incident));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.READ') or hasAuthority('CONTRACTOR.VIEW_REPORTS')")
    public List<LegalCounterpartyIncidentResponse> listIncidents(Long counterpartyId) {
        ensureExists(counterpartyId);
        return incidentRepository.findAllByCounterpartyIdOrderByIncidentDateDescIdDesc(counterpartyId).stream()
                .map(this::toIncidentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTRACTOR.REGISTRY.READ') or hasAuthority('OFFER.CALCULATE') or hasAuthority('CONTRACTOR.VIEW_REPORTS')")
    public LegalCounterpartyAssessmentResponse getAssessment(String inn) {
        String normalizedInn = normalizeRequired(inn, "inn");
        LegalCounterparty counterparty = counterpartyRepository.findByInn(normalizedInn)
                .orElseThrow(() -> new NotFoundException("Контрагент с ИНН " + normalizedInn + " не найден"));
        return toAssessmentResponse(counterparty);
    }

    private void applyEditableFields(LegalCounterparty counterparty, LegalCounterpartyUpsertRequest request, String normalizedInn) {
        String username = currentUsername();
        counterparty.setCompanyName(normalizeRequired(request.getCompanyName(), "companyName"));
        counterparty.setShortName(trimToNull(request.getShortName()));
        counterparty.setInn(normalizedInn);
        counterparty.setKpp(trimToNull(request.getKpp()));
        counterparty.setOgrn(trimToNull(request.getOgrn()));
        counterparty.setRegistryType(parseRequiredRegistryType(request.getRegistryType()));
        counterparty.setGeneralRisks(trimToNull(request.getGeneralRisks()));
        counterparty.setOverallScore(request.getOverallScore() == null ? 0 : request.getOverallScore());
        counterparty.setRiskLevel(parseOptionalRiskLevelOrDefault(request.getRiskLevel(), CounterpartyRiskLevel.UNKNOWN, "riskLevel"));
        counterparty.setWorkProhibited(Boolean.TRUE.equals(request.getWorkProhibited()));
        counterparty.setLastCheckedAt(toLocalDateTime(request.getLastCheckedAt()));
        counterparty.setLegalComment(trimToNull(request.getLegalComment()));
        counterparty.setUpdatedByUsername(username);

        counterparty.getTenderLinks().clear();
        if (request.getTenderLinks() != null) {
            request.getTenderLinks().stream()
                    .map(linkRequest -> toTenderLinkEntity(counterparty, linkRequest))
                    .forEach(counterparty.getTenderLinks()::add);
        }
    }

    private LegalCounterpartyTenderLink toTenderLinkEntity(
            LegalCounterparty counterparty,
            LegalCounterpartyTenderLinkRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("tenderLinks must not contain null values");
        }
        LegalCounterpartyTenderLink link = new LegalCounterpartyTenderLink();
        link.setCounterparty(counterparty);
        link.setTenderId(trimToNull(request.getTenderId()));
        link.setTenderName(trimToNull(request.getTenderName()));
        link.setTenderUrl(normalizeRequired(request.getTenderUrl(), "tenderUrl"));
        link.setRelationType(trimToNull(request.getRelationType()));
        return link;
    }

    private void validateUpsertRequest(LegalCounterpartyUpsertRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        normalizeRequired(request.getCompanyName(), "companyName");
        normalizeRequired(request.getInn(), "inn");
        parseRequiredRegistryType(request.getRegistryType());
        validateScore(request.getOverallScore());
        parseOptionalRiskLevelOrDefault(request.getRiskLevel(), CounterpartyRiskLevel.UNKNOWN, "riskLevel");
    }

    private void validateCheckRequest(LegalCounterpartyCheckCreateRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (request.getCheckedAt() == null) {
            throw new BadRequestException("checkedAt is required");
        }
        if (request.getWorkProhibited() == null) {
            throw new BadRequestException("workProhibited is required");
        }
        validateScore(request.getOverallScore());
        parseRequiredRiskLevel(request.getRiskLevel(), "riskLevel");
    }

    private void validateIncidentRequest(LegalCounterpartyIncidentCreateRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (request.getIncidentDate() == null) {
            throw new BadRequestException("incidentDate is required");
        }
        normalizeRequired(request.getTitle(), "title");
        parseOptionalImpactLevelOrDefault(request.getImpactLevel());
    }

    private void validateScore(Integer score) {
        if (score != null && (score < 0 || score > 100)) {
            throw new BadRequestException("overallScore must be between 0 and 100");
        }
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and 100");
        }
        return size;
    }

    private LegalCounterparty findById(Long counterpartyId) {
        if (counterpartyId == null) {
            throw new BadRequestException("counterpartyId is required");
        }
        return counterpartyRepository.findById(counterpartyId)
                .orElseThrow(() -> new NotFoundException("Контрагент не найден: " + counterpartyId));
    }

    private LegalCounterparty findByIdWithDetails(Long counterpartyId) {
        if (counterpartyId == null) {
            throw new BadRequestException("counterpartyId is required");
        }
        return counterpartyRepository.findById(counterpartyId)
                .orElseThrow(() -> new NotFoundException("Контрагент не найден: " + counterpartyId));
    }

    private void ensureExists(Long counterpartyId) {
        if (counterpartyId == null) {
            throw new BadRequestException("counterpartyId is required");
        }
        if (!counterpartyRepository.existsById(counterpartyId)) {
            throw new NotFoundException("Контрагент не найден: " + counterpartyId);
        }
    }

    private LegalCounterpartySummaryResponse toSummaryResponse(LegalCounterparty counterparty) {
        LegalCounterpartySummaryResponse response = new LegalCounterpartySummaryResponse();
        response.setId(counterparty.getId());
        response.setCompanyName(counterparty.getCompanyName());
        response.setShortName(counterparty.getShortName());
        response.setInn(counterparty.getInn());
        response.setKpp(counterparty.getKpp());
        response.setOgrn(counterparty.getOgrn());
        response.setRegistryType(toName(counterparty.getRegistryType()));
        response.setOverallScore(counterparty.getOverallScore());
        response.setRiskLevel(toName(counterparty.getRiskLevel()));
        response.setWorkProhibited(counterparty.isWorkProhibited());
        response.setLastCheckedAt(toOffsetDateTime(counterparty.getLastCheckedAt()));
        response.setLegalComment(counterparty.getLegalComment());
        response.setUpdatedAt(toOffsetDateTime(counterparty.getUpdatedAt()));
        return response;
    }

    private LegalCounterpartyDetailsResponse toDetailsResponse(LegalCounterparty counterparty) {
        LegalCounterpartyDetailsResponse response = new LegalCounterpartyDetailsResponse();
        response.setId(counterparty.getId());
        response.setCompanyName(counterparty.getCompanyName());
        response.setShortName(counterparty.getShortName());
        response.setInn(counterparty.getInn());
        response.setKpp(counterparty.getKpp());
        response.setOgrn(counterparty.getOgrn());
        response.setRegistryType(toName(counterparty.getRegistryType()));
        response.setOverallScore(counterparty.getOverallScore());
        response.setRiskLevel(toName(counterparty.getRiskLevel()));
        response.setWorkProhibited(counterparty.isWorkProhibited());
        response.setLastCheckedAt(toOffsetDateTime(counterparty.getLastCheckedAt()));
        response.setLegalComment(counterparty.getLegalComment());
        response.setUpdatedAt(toOffsetDateTime(counterparty.getUpdatedAt()));
        response.setGeneralRisks(counterparty.getGeneralRisks());
        response.setTenderLinks(counterparty.getTenderLinks().stream().map(this::toTenderLinkResponse).toList());
        response.setChecks(counterparty.getChecks().stream().map(this::toCheckResponse).toList());
        response.setIncidents(counterparty.getIncidents().stream().map(this::toIncidentResponse).toList());
        response.setCreatedByUsername(counterparty.getCreatedByUsername());
        response.setUpdatedByUsername(counterparty.getUpdatedByUsername());
        response.setCreatedAt(toOffsetDateTime(counterparty.getCreatedAt()));
        return response;
    }

    private LegalCounterpartyCheckResponse toCheckResponse(LegalCounterpartyCheck check) {
        LegalCounterpartyCheckResponse response = new LegalCounterpartyCheckResponse();
        response.setId(check.getId());
        response.setCheckedAt(toOffsetDateTime(check.getCheckedAt()));
        response.setOverallScore(check.getOverallScore());
        response.setRiskLevel(toName(check.getRiskLevel()));
        response.setWorkProhibited(check.isWorkProhibited());
        response.setRisks(check.getRisks());
        response.setComment(check.getComment());
        response.setCheckedByUsername(check.getCheckedByUsername());
        response.setCreatedAt(toOffsetDateTime(check.getCreatedAt()));
        return response;
    }

    private LegalCounterpartyIncidentResponse toIncidentResponse(LegalCounterpartyIncident incident) {
        LegalCounterpartyIncidentResponse response = new LegalCounterpartyIncidentResponse();
        response.setId(incident.getId());
        response.setIncidentDate(incident.getIncidentDate());
        response.setTitle(incident.getTitle());
        response.setDescription(incident.getDescription());
        response.setImpactLevel(toName(incident.getImpactLevel()));
        response.setCreatedByUsername(incident.getCreatedByUsername());
        response.setCreatedAt(toOffsetDateTime(incident.getCreatedAt()));
        response.setUpdatedAt(toOffsetDateTime(incident.getUpdatedAt()));
        return response;
    }

    private LegalCounterpartyTenderLinkResponse toTenderLinkResponse(LegalCounterpartyTenderLink link) {
        LegalCounterpartyTenderLinkResponse response = new LegalCounterpartyTenderLinkResponse();
        response.setId(link.getId());
        response.setTenderId(link.getTenderId());
        response.setTenderName(link.getTenderName());
        response.setTenderUrl(link.getTenderUrl());
        response.setRelationType(link.getRelationType());
        response.setCreatedAt(toOffsetDateTime(link.getCreatedAt()));
        response.setUpdatedAt(toOffsetDateTime(link.getUpdatedAt()));
        return response;
    }

    private LegalCounterpartyAssessmentResponse toAssessmentResponse(LegalCounterparty counterparty) {
        LegalCounterpartyAssessmentResponse response = new LegalCounterpartyAssessmentResponse();
        response.setId(counterparty.getId());
        response.setCompanyName(counterparty.getCompanyName());
        response.setInn(counterparty.getInn());
        response.setRegistryType(toName(counterparty.getRegistryType()));
        response.setOverallScore(counterparty.getOverallScore());
        response.setRiskLevel(toName(counterparty.getRiskLevel()));
        response.setWorkProhibited(counterparty.isWorkProhibited());
        response.setLastCheckedAt(toOffsetDateTime(counterparty.getLastCheckedAt()));
        response.setGeneralRisks(counterparty.getGeneralRisks());
        return response;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Unauthorized");
        }
        return authentication.getName();
    }

    private CounterpartyRegistryType parseRequiredRegistryType(String value) {
        String normalized = normalizeRequired(value, "registryType");
        try {
            return CounterpartyRegistryType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("registryType must be one of CUSTOMER, SUPPLIER, BOTH");
        }
    }

    private CounterpartyRegistryType parseOptionalRegistryType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequiredRegistryType(value);
    }

    private CounterpartyRiskLevel parseRequiredRiskLevel(String value, String fieldName) {
        String normalized = normalizeRequired(value, fieldName);
        try {
            return CounterpartyRiskLevel.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(fieldName + " must be one of UNKNOWN, LOW, MEDIUM, HIGH, CRITICAL");
        }
    }

    private CounterpartyRiskLevel parseOptionalRiskLevel(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequiredRiskLevel(value, fieldName);
    }

    private CounterpartyRiskLevel parseOptionalRiskLevelOrDefault(
            String value,
            CounterpartyRiskLevel defaultValue,
            String fieldName
    ) {
        CounterpartyRiskLevel parsed = parseOptionalRiskLevel(value, fieldName);
        return parsed == null ? defaultValue : parsed;
    }

    private CounterpartyRiskLevel parseOptionalImpactLevelOrDefault(String value) {
        CounterpartyRiskLevel parsed = parseOptionalRiskLevel(value, "impactLevel");
        if (parsed == null) {
            return CounterpartyRiskLevel.MEDIUM;
        }
        if (parsed == CounterpartyRiskLevel.UNKNOWN) {
            throw new BadRequestException("impactLevel must be one of LOW, MEDIUM, HIGH, CRITICAL");
        }
        return parsed;
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    @SuppressWarnings("unused")
    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
