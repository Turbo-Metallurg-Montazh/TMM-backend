package com.kindred.emkcrm_project_backend.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kindred.emkcrm_project_backend.db.entities.TenderFilter;
import com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity.FoundTender;
import com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity.FoundTendersArray;
import com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity.Participant;
import com.kindred.emkcrm_project_backend.db.repositories.TenderFilterRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.NotFoundException;
import com.kindred.emkcrm_project_backend.model.ExportTendersByFilterRequest;
import com.kindred.emkcrm_project_backend.model.ExportTendersByFilterResponse;
import com.kindred.emkcrm_project_backend.model.FoundTenderParticipantResponse;
import com.kindred.emkcrm_project_backend.model.FoundTenderResponse;
import com.kindred.emkcrm_project_backend.model.FoundTendersResponse;
import com.kindred.emkcrm_project_backend.utils.FindTenders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class TenderFilterExportService {

    private static final int FIRST_PAGE = 1;
    private static final int SINGLE_PAGE = 1;
    private static final int MAX_EXPORT_PAGES = 20;

    private final TenderFilterRepository tenderFilterRepository;
    private final FindTenders findTenders;
    private final Clock clock;

    public TenderFilterExportService(
            TenderFilterRepository tenderFilterRepository,
            FindTenders findTenders,
            Clock clock
    ) {
        this.tenderFilterRepository = tenderFilterRepository;
        this.findTenders = findTenders;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('TENDER_FILTER.WRITE')")
    public ExportTendersByFilterResponse exportTendersByFilter(ExportTendersByFilterRequest request) {
        validateRequest(request);

        String filterName = request.getFilterName().trim();
        TenderFilter tenderFilter = tenderFilterRepository.findByName(filterName)
                .orElseThrow(() -> new NotFoundException("Фильтр не найден: " + filterName));

        SearchRange searchRange = resolveSearchRange(
                request.getDateFrom() == null ? tenderFilter.getDateTimeFrom() : request.getDateFrom().toInstant(),
                request.getDateTo() == null ? tenderFilter.getDateTimeTo() : request.getDateTo().toInstant()
        );
        Instant dateFrom = searchRange.dateFrom();
        Instant dateTo = searchRange.dateTo();
        if (dateFrom.isAfter(dateTo)) {
            throw new BadRequestException("dateFrom must be <= dateTo");
        }

        int toPage = Boolean.TRUE.equals(request.getLoadAllPages()) ? MAX_EXPORT_PAGES : SINGLE_PAGE;

        try {
            FoundTendersArray exportedTenders = findTenders.findTenders(
                    tenderFilter,
                    dateFrom.toString(),
                    dateTo.toString(),
                    FIRST_PAGE,
                    toPage
            );
            return toResponse(filterName, exportedTenders);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to export tenders by filter", e);
        }
    }

    private void validateRequest(ExportTendersByFilterRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (request.getFilterName() == null || request.getFilterName().isBlank()) {
            throw new BadRequestException("filterName is required");
        }
    }

    SearchRange resolveSearchRange(Instant dateFrom, Instant dateTo) {
        Instant resolvedDateTo = dateTo == null ? Instant.now(clock) : dateTo;
        Instant resolvedDateFrom = dateFrom == null
                ? OffsetDateTime.ofInstant(resolvedDateTo, ZoneOffset.UTC).minusYears(1).toInstant()
                : dateFrom;
        return new SearchRange(resolvedDateFrom, resolvedDateTo);
    }

    private ExportTendersByFilterResponse toResponse(String filterName, FoundTendersArray exportedTenders) {
        ExportTendersByFilterResponse response = new ExportTendersByFilterResponse();
        response.setFilterName(filterName);
        response.setFromDate(toOffsetDateTime(exportedTenders.getFromDate()));
        response.setToDate(toOffsetDateTime(exportedTenders.getToDate()));
        response.setTendersDownloadCount(exportedTenders.getTendersDownloadCount());
        response.setTotalPagesCount(exportedTenders.getTotalPagesCount());
        response.setLoadedPagesCount(exportedTenders.getLoadedPagesCount());
        response.setFoundTenders(toFoundTendersResponse(exportedTenders));
        return response;
    }

    private FoundTendersResponse toFoundTendersResponse(FoundTendersArray exportedTenders) {
        FoundTendersResponse response = new FoundTendersResponse();
        if (exportedTenders.getFoundTenders() == null) {
            response.setTotalCount(0);
            response.setItems(List.of());
            return response;
        }

        response.setTotalCount(exportedTenders.getFoundTenders().getTotalCount());
        response.setItems(toTenderResponses(exportedTenders.getFoundTenders().getFoundTenders()));
        return response;
    }

    private List<FoundTenderResponse> toTenderResponses(List<FoundTender> tenders) {
        if (tenders == null || tenders.isEmpty()) {
            return List.of();
        }

        List<FoundTenderResponse> responses = new ArrayList<>(tenders.size());
        for (FoundTender tender : tenders) {
            FoundTenderResponse response = new FoundTenderResponse();
            response.setId(tender.getId());
            response.setNotificationNumber(tender.getNotificationNumber());
            response.setOrderName(tender.getOrderName());
            response.setNotificationTypeDesc(tender.getNotificationTypeDesc());
            response.setTypeOfTrading(tender.getTypeOfTrading());
            response.setMaxPrice(tender.getMaxPrice());
            response.setCurrency(tender.getCurrency());
            response.setEpUri(tender.getEpUri());
            response.setLink(tender.getLink());
            response.setApplicationDeadline(toOffsetDateTime(tender.getApplicationDeadline()));
            response.setIsCancelled(tender.isCancelled());
            response.setCreateDate(toOffsetDateTime(tender.getCreateDate()));
            response.setDeliveryPlaces(tender.getDeliveryPlaces());
            response.setInns(tender.getInns());
            response.setParticipants(toParticipantResponses(tender.getParticipants()));
            response.setOtherInformation(tender.getOtherInformation());
            response.setCommissionDeadline(toOffsetDateTime(tender.getCommissionDeadline()));
            response.setIsAbandoned(tender.isAbandoned());
            response.setIsPlanning(tender.isPlanning());
            responses.add(response);
        }
        return responses;
    }

    private List<FoundTenderParticipantResponse> toParticipantResponses(List<Participant> participants) {
        if (participants == null || participants.isEmpty()) {
            return List.of();
        }

        List<FoundTenderParticipantResponse> responses = new ArrayList<>(participants.size());
        for (Participant participant : participants) {
            FoundTenderParticipantResponse response = new FoundTenderParticipantResponse();
            response.setInn(participant.getInn());
            response.setIsWinner(participant.isWinner());
            response.setIsContractor(participant.isContractor());
            response.setFoundFromDocuments(participant.isFoundFromDocuments());
            responses.add(response);
        }
        return responses;
    }

    private OffsetDateTime toOffsetDateTime(Date value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    record SearchRange(
            Instant dateFrom,
            Instant dateTo
    ) {
    }
}
