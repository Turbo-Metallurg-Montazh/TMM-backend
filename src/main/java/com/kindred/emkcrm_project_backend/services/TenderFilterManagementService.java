package com.kindred.emkcrm_project_backend.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kindred.emkcrm_project_backend.authentication.UserService;
import com.kindred.emkcrm_project_backend.db.entities.TenderFilter;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.TenderFilterRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.exception.NotFoundException;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import com.kindred.emkcrm_project_backend.model.AddTenderFilterRequest;
import com.kindred.emkcrm_project_backend.model.ParseTenderFilterRequest;
import com.kindred.emkcrm_project_backend.model.TenderFilterDetailsResponse;
import com.kindred.emkcrm_project_backend.model.TenderFilterSummaryResponse;
import com.kindred.emkcrm_project_backend.utils.json.TenderJsonMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TenderFilterManagementService {

    private static final String CREATED_MESSAGE = "Фильтр для тендеров успешно добавлен в БД";

    private final TenderFilterRepository tenderFilterRepository;
    private final TenderJsonMapper tenderJsonMapper;
    private final UserService userService;
    private final TenderFilterUrlParser tenderFilterUrlParser;

    public TenderFilterManagementService(
            TenderFilterRepository tenderFilterRepository,
            TenderJsonMapper tenderJsonMapper,
            UserService userService,
            TenderFilterUrlParser tenderFilterUrlParser
    ) {
        this.tenderFilterRepository = tenderFilterRepository;
        this.tenderJsonMapper = tenderJsonMapper;
        this.userService = userService;
        this.tenderFilterUrlParser = tenderFilterUrlParser;
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_FILTER.WRITE')")
    public String addTenderFilter(AddTenderFilterRequest request) {
        validateRequest(request);
        String filterName = request.getName().trim();
        ensureNameIsAvailable(filterName);

        User user = currentUser();
        ensureFilterDoesNotExist(user.getId(), request);

        tenderFilterRepository.save(toEntity(request, user.getId(), filterName));
        return CREATED_MESSAGE;
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_FILTER.WRITE')")
    public String parseTenderFilter(ParseTenderFilterRequest request) {
        validateParseRequest(request);

        TenderFilterUrlParser.ParsedTenderFilter parsedTenderFilter = tenderFilterUrlParser.parse(request.getFilter());
        AddTenderFilterRequest parsedRequest = parsedTenderFilter.request();
        String filterName = tenderFilterUrlParser.resolveFilterName(request.getName(), parsedTenderFilter.rawText());
        parsedRequest.setName(filterName);

        ensureNameIsAvailable(filterName);

        User user = currentUser();
        ensureFilterDoesNotExist(user.getId(), parsedRequest);

        tenderFilterRepository.save(toEntity(parsedRequest, user.getId(), filterName));
        return filterName;
    }

    @PreAuthorize("hasAuthority('TENDER_FILTER.WRITE')")
    public List<TenderFilterSummaryResponse> listTenderFilters() {
        return tenderFilterRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('TENDER_FILTER.WRITE')")
    public TenderFilterDetailsResponse getTenderFilterByName(String filterName) {
        return toDetailsResponse(findByName(filterName));
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_FILTER.WRITE')")
    public TenderFilterDetailsResponse updateTenderFilterByName(String filterName, AddTenderFilterRequest request) {
        validateRequest(request);
        TenderFilter tenderFilter = findByName(filterName);
        String updatedName = request.getName().trim();

        ensureNameIsAvailableForUpdate(updatedName, tenderFilter.getId());
        applyEditableFields(tenderFilter, request, updatedName);
        return toDetailsResponse(tenderFilterRepository.save(tenderFilter));
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_FILTER.WRITE')")
    public String deleteTenderFilterByName(String filterName) {
        TenderFilter tenderFilter = findByName(filterName);
        tenderFilterRepository.delete(tenderFilter);
        return "Фильтр успешно удален";
    }

    private void ensureNameIsAvailable(String filterName) {
        if (tenderFilterRepository.existsByName(filterName)) {
            throw new ConflictException(String.format("Фильтр с названием %s уже существует", filterName));
        }
    }

    private void ensureNameIsAvailableForUpdate(String filterName, Long filterId) {
        if (tenderFilterRepository.existsByNameAndIdNot(filterName, filterId)) {
            throw new ConflictException(String.format("Фильтр с названием %s уже существует", filterName));
        }
    }

    private void ensureFilterDoesNotExist(Long userId, AddTenderFilterRequest request) {
        TenderFilter candidateFilter = toEntity(request, userId, request.getName() == null ? "" : request.getName());
        ensureFilterDoesNotExist(userId, candidateFilter);
    }

    private void ensureFilterDoesNotExist(Long userId, TenderFilter candidateFilter) {
        try {
            String requestedFilter = tenderJsonMapper.serializeFilter(candidateFilter);
            for (TenderFilter existingFilter : tenderFilterRepository.findAllByUserId(userId)) {
                if (tenderJsonMapper.serializeFilter(existingFilter).equals(requestedFilter)) {
                    throw new ConflictException(String.format("Фильтр уже существует под названием %s", existingFilter.getName()));
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tender filter", e);
        }
    }

    private TenderFilter toEntity(AddTenderFilterRequest request, Long userId, String filterName) {
        TenderFilter tenderFilter = new TenderFilter();
        tenderFilter.setUserId(userId);
        applyEditableFields(tenderFilter, request, filterName);
        return tenderFilter;
    }

    private void applyEditableFields(TenderFilter tenderFilter, AddTenderFilterRequest request, String filterName) {
        tenderFilter.setName(filterName);
        tenderFilter.setActive(request.getActive() == null || request.getActive());
        tenderFilter.setText(toStringArray(request.getText()));
        tenderFilter.setExclude(toStringArray(request.getExclude()));
        tenderFilter.setCategories(toIntegerArray(request.getCategories()));
        tenderFilter.setIncludeInns(toStringArray(request.getIncludeInns()));
        tenderFilter.setExcludeInns(toStringArray(request.getExcludeInns()));
        tenderFilter.setDateTimeFrom(toInstant(request.getDateTimeFrom()));
        tenderFilter.setDateTimeTo(toInstant(request.getDateTimeTo()));
        tenderFilter.setParticipantsInns(toStringArray(request.getParticipantsInns()));
        tenderFilter.setParticipantsState(request.getParticipantsState());
        tenderFilter.setEnableParticipantsFromDocuments(request.getEnableParticipantsFromDocuments());
        tenderFilter.setRegionIds(toStringArray(request.getRegionIds()));
        tenderFilter.setPurchaseStatuses(toIntegerArray(request.getPurchaseStatuses()));
        tenderFilter.setLaws(toIntegerArray(request.getLaws()));
        tenderFilter.setProcedures(toIntegerArray(request.getProcedures()));
        tenderFilter.setElectronicPlaces(toIntegerArray(request.getElectronicPlaces()));
        tenderFilter.setCategoryIds(toIntegerArray(request.getCategoryIds()));
        tenderFilter.setStrictSearch(request.getStrictSearch());
        tenderFilter.setAttachments(request.getAttachments());
        tenderFilter.setMaxPriceFrom(request.getMaxPriceFrom());
        tenderFilter.setMaxPriceTo(request.getMaxPriceTo());
        tenderFilter.setMaxPriceNone(request.getMaxPriceNone());
        tenderFilter.setAdvance44(request.getAdvance44());
        tenderFilter.setAdvance223(request.getAdvance223());
        tenderFilter.setNonAdvance(request.getNonAdvance());
        tenderFilter.setSmp(request.getSmp());
        tenderFilter.setAllowForeignCurrency(request.getAllowForeignCurrency());
        tenderFilter.setPageNumber(request.getPageNumber());
        tenderFilter.setSortOrder(request.getSortOrder());
        tenderFilter.setApplicationDeadlineFrom(toInstant(request.getApplicationDeadlineFrom()));
        tenderFilter.setApplicationDeadlineTo(toInstant(request.getApplicationDeadlineTo()));
        tenderFilter.setApplicationDeadlineType(request.getApplicationDeadlineType());
        tenderFilter.setIncludedRequirementIds(toIntegerArray(request.getIncludedRequirementIds()));
        tenderFilter.setExcludedRequirementIds(toIntegerArray(request.getExcludedRequirementIds()));
    }

    private void validateRequest(AddTenderFilterRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }
        if (request.getDateTimeFrom() != null
                && request.getDateTimeTo() != null
                && request.getDateTimeFrom().toInstant().isAfter(request.getDateTimeTo().toInstant())) {
            throw new BadRequestException("dateTimeFrom must be <= dateTimeTo");
        }
        if (request.getApplicationDeadlineFrom() != null
                && request.getApplicationDeadlineTo() != null
                && request.getApplicationDeadlineFrom().toInstant().isAfter(request.getApplicationDeadlineTo().toInstant())) {
            throw new BadRequestException("applicationDeadlineFrom must be <= applicationDeadlineTo");
        }
    }

    private void validateParseRequest(ParseTenderFilterRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (request.getFilter() == null || request.getFilter().isBlank()) {
            throw new BadRequestException("filter is required");
        }
    }

    private TenderFilter findByName(String filterName) {
        if (filterName == null || filterName.isBlank()) {
            throw new BadRequestException("filterName is required");
        }
        String normalizedFilterName = filterName.trim();
        return tenderFilterRepository.findByName(normalizedFilterName)
                .orElseThrow(() -> new NotFoundException("Фильтр не найден: " + normalizedFilterName));
    }

    private TenderFilterSummaryResponse toSummaryResponse(TenderFilter tenderFilter) {
        TenderFilterSummaryResponse response = new TenderFilterSummaryResponse();
        response.setName(tenderFilter.getName());
        response.setActive(tenderFilter.isActive());
        return response;
    }

    private TenderFilterDetailsResponse toDetailsResponse(TenderFilter tenderFilter) {
        TenderFilterDetailsResponse response = new TenderFilterDetailsResponse();
        response.setId(tenderFilter.getId());
        response.setName(tenderFilter.getName());
        response.setUserId(tenderFilter.getUserId());
        response.setActive(tenderFilter.isActive());
        response.setText(toStringList(tenderFilter.getText()));
        response.setExclude(toStringList(tenderFilter.getExclude()));
        response.setCategories(toIntegerList(tenderFilter.getCategories()));
        response.setIncludeInns(toStringList(tenderFilter.getIncludeInns()));
        response.setExcludeInns(toStringList(tenderFilter.getExcludeInns()));
        response.setDateTimeFrom(toOffsetDateTime(tenderFilter.getDateTimeFrom()));
        response.setDateTimeTo(toOffsetDateTime(tenderFilter.getDateTimeTo()));
        response.setParticipantsInns(toStringList(tenderFilter.getParticipantsInns()));
        response.setParticipantsState(tenderFilter.getParticipantsState());
        response.setEnableParticipantsFromDocuments(tenderFilter.getEnableParticipantsFromDocuments());
        response.setRegionIds(toUuidList(tenderFilter.getRegionIds()));
        response.setPurchaseStatuses(toIntegerList(tenderFilter.getPurchaseStatuses()));
        response.setLaws(toIntegerList(tenderFilter.getLaws()));
        response.setProcedures(toIntegerList(tenderFilter.getProcedures()));
        response.setElectronicPlaces(toIntegerList(tenderFilter.getElectronicPlaces()));
        response.setCategoryIds(toIntegerList(tenderFilter.getCategoryIds()));
        response.setStrictSearch(tenderFilter.getStrictSearch());
        response.setAttachments(tenderFilter.getAttachments());
        response.setMaxPriceFrom(tenderFilter.getMaxPriceFrom());
        response.setMaxPriceTo(tenderFilter.getMaxPriceTo());
        response.setMaxPriceNone(tenderFilter.getMaxPriceNone());
        response.setAdvance44(tenderFilter.getAdvance44());
        response.setAdvance223(tenderFilter.getAdvance223());
        response.setNonAdvance(tenderFilter.getNonAdvance());
        response.setSmp(tenderFilter.getSmp());
        response.setAllowForeignCurrency(tenderFilter.getAllowForeignCurrency());
        response.setPageNumber(tenderFilter.getPageNumber());
        response.setSortOrder(tenderFilter.getSortOrder());
        response.setApplicationDeadlineFrom(toOffsetDateTime(tenderFilter.getApplicationDeadlineFrom()));
        response.setApplicationDeadlineTo(toOffsetDateTime(tenderFilter.getApplicationDeadlineTo()));
        response.setApplicationDeadlineType(tenderFilter.getApplicationDeadlineType());
        response.setIncludedRequirementIds(toIntegerList(tenderFilter.getIncludedRequirementIds()));
        response.setExcludedRequirementIds(toIntegerList(tenderFilter.getExcludedRequirementIds()));
        response.setCreatedAt(toOffsetDateTime(tenderFilter.getCreatedAt()));
        response.setUpdatedAt(toOffsetDateTime(tenderFilter.getUpdatedAt()));
        return response;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Unauthorized");
        }

        User user = userService.findUserWithRolesByUsername(authentication.getName());
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return user;
    }

    private String[] toStringArray(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .map(value -> value == null ? null : value.toString())
                .toArray(String[]::new);
    }

    private Integer[] toIntegerArray(List<Integer> values) {
        return values == null || values.isEmpty() ? null : values.toArray(Integer[]::new);
    }

    private Instant toInstant(java.time.OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private List<String> toStringList(String[] values) {
        return values == null ? null : List.of(values);
    }

    private List<Integer> toIntegerList(Integer[] values) {
        return values == null ? null : List.of(values);
    }

    private List<UUID> toUuidList(String[] values) {
        if (values == null) {
            return null;
        }
        return java.util.Arrays.stream(values)
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
