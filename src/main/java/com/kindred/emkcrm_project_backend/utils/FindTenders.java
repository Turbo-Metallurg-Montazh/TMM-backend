package com.kindred.emkcrm_project_backend.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kindred.emkcrm_project_backend.config.PaginationProperties;
import com.kindred.emkcrm_project_backend.db.entities.TenderFilter;
import com.kindred.emkcrm_project_backend.db.repositories.FoundTenderRepository;
import com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity.FoundTender;
import com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity.FoundTenders;
import com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity.FoundTendersArray;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ServiceUnavailableException;
import com.kindred.emkcrm_project_backend.external.KonturExternalApiService;
import com.kindred.emkcrm_project_backend.utils.json.TenderJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;


@Slf4j
@Component
public class FindTenders {
    private final FoundTenderRepository foundTenderRepository;
    private final PaginationProperties paginationProperties;
    private final KonturExternalApiService konturExternalApiService;
    private final TenderJsonMapper tenderJsonMapper;

    public FindTenders(
            FoundTenderRepository foundTenderRepository,
            PaginationProperties paginationProperties,
            KonturExternalApiService konturExternalApiService,
            TenderJsonMapper tenderJsonMapper
    ) {
        this.foundTenderRepository = foundTenderRepository;
        this.paginationProperties = paginationProperties;
        this.konturExternalApiService = konturExternalApiService;
        this.tenderJsonMapper = tenderJsonMapper;
    }


    public FoundTendersArray findTenders(TenderFilter tenderFilter, String dateFromInstant, String dateToInstant, int fromPage, int toPage) throws JsonProcessingException {
        Objects.requireNonNull(tenderFilter, "tenderFilter must not be null");
        validatePages(fromPage, toPage);
        Date fromDate = parseInstant(dateFromInstant, "dateFromInstant");
        Date toDate = parseInstant(dateToInstant, "dateToInstant");

        int firstPageIndex = fromPage - 1;
        String firstPageRequest = tenderJsonMapper.buildSearchPayload(tenderFilter, dateFromInstant, dateToInstant, firstPageIndex);
        String firstResponse = searchPurchases(firstPageRequest);

        FoundTenders foundTenders = tenderJsonMapper.readFoundTenders(firstResponse);
        ArrayList<FoundTender> collectedTenders = safeTenders(foundTenders);

        int totalPages = Math.max(0, (int) Math.ceil((double) foundTenders.getTotalCount() / paginationProperties.items_on_page()));
        int maxPageIndex = Math.max(firstPageIndex, totalPages - 1);
        int lastPageIndex = Math.min(toPage - 1, maxPageIndex);

        for (int pageIndex = firstPageIndex + 1; pageIndex <= lastPageIndex; pageIndex++) {
            String nextPageRequest = tenderJsonMapper.buildSearchPayload(tenderFilter, dateFromInstant, dateToInstant, pageIndex);
            String nextResponse = searchPurchases(nextPageRequest);
            collectedTenders.addAll(safeTenders(tenderJsonMapper.readFoundTenders(nextResponse)));
        }

        foundTenders.setFoundTenders(collectedTenders);

        FoundTendersArray foundTendersArray = new FoundTendersArray();
        foundTendersArray.setFoundTenders(foundTenders);
        foundTendersArray.setTendersDownloadCount(collectedTenders.size());
        foundTendersArray.setFromDate(fromDate);
        foundTendersArray.setToDate(toDate);
        foundTendersArray.setTotalPagesCount(foundTenders.getTotalCount(), paginationProperties.items_on_page());

        foundTenderRepository.saveAll(collectedTenders);
        log.info("Loaded {} tenders from external API for period {} - {}", collectedTenders.size(), dateFromInstant, dateToInstant);

        return foundTendersArray;
    }

    private String searchPurchases(String payload) {
        ResponseEntity<String> responseEntity = konturExternalApiService.searchPurchasesRaw(payload);
        if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null || responseEntity.getBody().isBlank()) {
            throw new ServiceUnavailableException("Failed to search purchases in external service");
        }
        return responseEntity.getBody();
    }

    private ArrayList<FoundTender> safeTenders(FoundTenders foundTenders) {
        return foundTenders.getFoundTenders() == null ? new ArrayList<>() : new ArrayList<>(foundTenders.getFoundTenders());
    }

    private Date parseInstant(String instantString, String fieldName) {
        try {
            Instant instant = Instant.parse(instantString);
            return Date.from(instant);
        } catch (RuntimeException e) {
            throw new BadRequestException("Invalid instant in " + fieldName);
        }
    }

    private void validatePages(int fromPage, int toPage) {
        if (fromPage < 1) {
            throw new BadRequestException("fromPage must be >= 1");
        }
        if (toPage < fromPage) {
            throw new BadRequestException("toPage must be >= fromPage");
        }
    }
}
