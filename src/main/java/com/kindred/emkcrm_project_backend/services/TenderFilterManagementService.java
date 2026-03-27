package com.kindred.emkcrm_project_backend.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kindred.emkcrm_project_backend.authentication.UserService;
import com.kindred.emkcrm_project_backend.db.entities.TenderFilter;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.TenderFilterRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import com.kindred.emkcrm_project_backend.model.AddTenderFilterRequest;
import com.kindred.emkcrm_project_backend.utils.json.TenderJsonMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TenderFilterManagementService {

    private static final String CREATED_MESSAGE = "Фильтр для тендеров успешно добавлен в БД";

    private final TenderFilterRepository tenderFilterRepository;
    private final TenderJsonMapper tenderJsonMapper;
    private final UserService userService;

    public TenderFilterManagementService(
            TenderFilterRepository tenderFilterRepository,
            TenderJsonMapper tenderJsonMapper,
            UserService userService
    ) {
        this.tenderFilterRepository = tenderFilterRepository;
        this.tenderJsonMapper = tenderJsonMapper;
        this.userService = userService;
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_FILTER.WRITE')")
    public String addTenderFilter(AddTenderFilterRequest request) {
        validateRequest(request);

        User user = currentUser();
        ensureFilterDoesNotExist(user.getId(), request);

        tenderFilterRepository.save(toEntity(request, user.getId()));
        return CREATED_MESSAGE;
    }

    private void ensureFilterDoesNotExist(Long userId, AddTenderFilterRequest request) {
        try {
            String requestedFilter = tenderJsonMapper.serializeFilter(request);
            for (TenderFilter existingFilter : tenderFilterRepository.findAllByUserId(userId)) {
                if (tenderJsonMapper.serializeFilter(existingFilter).equals(requestedFilter)) {
                    throw new ConflictException(String.format("Фильтр уже существует под названием %s", existingFilter.getName()));
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tender filter", e);
        }
    }

    private TenderFilter toEntity(AddTenderFilterRequest request, Long userId) {
        TenderFilter tenderFilter = new TenderFilter();
        tenderFilter.setName(request.getName().trim());
        tenderFilter.setUserId(userId);
        tenderFilter.setActive(request.getActive() == null || request.getActive());
        tenderFilter.setText(toStringArray(request.getText()));
        tenderFilter.setExclude(toStringArray(request.getExclude()));
        tenderFilter.setCategories(toIntegerArray(request.getCategories()));
        tenderFilter.setIncludeInns(toStringArray(request.getIncludeInns()));
        tenderFilter.setExcludeInns(toStringArray(request.getExcludeInns()));
        tenderFilter.setDateTimeFrom(request.getDateTimeFrom().toInstant());
        tenderFilter.setDateTimeTo(request.getDateTimeTo().toInstant());
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
        tenderFilter.setApplicationDeadlineFrom(toInstant(request.getApplicationDeadlineFrom()));
        tenderFilter.setApplicationDeadlineTo(toInstant(request.getApplicationDeadlineTo()));
        return tenderFilter;
    }

    private void validateRequest(AddTenderFilterRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }
        if (request.getDateTimeFrom() == null) {
            throw new BadRequestException("dateTimeFrom is required");
        }
        if (request.getDateTimeTo() == null) {
            throw new BadRequestException("dateTimeTo is required");
        }
        if (request.getDateTimeFrom().toInstant().isAfter(request.getDateTimeTo().toInstant())) {
            throw new BadRequestException("dateTimeFrom must be <= dateTimeTo");
        }
        if (request.getApplicationDeadlineFrom() != null
                && request.getApplicationDeadlineTo() != null
                && request.getApplicationDeadlineFrom().toInstant().isAfter(request.getApplicationDeadlineTo().toInstant())) {
            throw new BadRequestException("applicationDeadlineFrom must be <= applicationDeadlineTo");
        }
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
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(value -> value == null ? null : value.toString())
                .toArray(String[]::new);
    }

    private Integer[] toIntegerArray(List<Integer> values) {
        return values == null ? null : values.toArray(Integer[]::new);
    }

    private Instant toInstant(java.time.OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
