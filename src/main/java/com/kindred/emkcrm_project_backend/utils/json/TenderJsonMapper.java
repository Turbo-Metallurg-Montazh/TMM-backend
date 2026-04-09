package com.kindred.emkcrm_project_backend.utils.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kindred.emkcrm_project_backend.db.entities.TenderFilter;
import com.kindred.emkcrm_project_backend.db.entities.favoritesEntity.FavoriteMarkersPage;
import com.kindred.emkcrm_project_backend.db.entities.favoritesEntity.FavoriteTendersPage;
import com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity.FoundTender;
import com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity.FoundTenders;
import com.kindred.emkcrm_project_backend.db.entities.purchaseResultsEntity.PurchaseResult;
import com.kindred.emkcrm_project_backend.db.entities.tenderEntity.Tender;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.model.AddTenderFilterRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.Instant;
import java.time.OffsetDateTime;

@Component
public class TenderJsonMapper {

    private static final int MAX_RAW_ITEM_JSON_LENGTH = 100_000;
    private static final StreamReadConstraints EXTERNAL_STREAM_READ_CONSTRAINTS = StreamReadConstraints.builder()
            .maxNestingDepth(200)
            .maxStringLength(1_000_000)
            .build();

    private final ObjectMapper objectMapper;
    private final ObjectReader tenderReader;
    private final ObjectReader purchaseResultReader;
    private final ObjectReader favoriteTendersPageReader;
    private final ObjectReader favoriteMarkersPageReader;
    private final ObjectWriter nonNullWriter;

    public TenderJsonMapper() {
        this.objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
        this.objectMapper.getFactory().setStreamReadConstraints(EXTERNAL_STREAM_READ_CONSTRAINTS);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);

        this.tenderReader = this.objectMapper.readerFor(Tender.class);
        this.purchaseResultReader = this.objectMapper.readerFor(PurchaseResult.class);
        this.favoriteTendersPageReader = this.objectMapper.readerFor(FavoriteTendersPage.class);
        this.favoriteMarkersPageReader = this.objectMapper.readerFor(FavoriteMarkersPage.class);

        ObjectMapper nonNullMapper = this.objectMapper.copy();
        nonNullMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        this.nonNullWriter = nonNullMapper.writer();
    }

    public String serializeFilter(AddTenderFilterRequest request) throws JsonProcessingException {
        Objects.requireNonNull(request, "request must not be null");
        return nonNullWriter.writeValueAsString(buildComparablePayloadNode(snapshot(request)));
    }

    public String serializeFilter(TenderFilter tenderFilter) throws JsonProcessingException {
        Objects.requireNonNull(tenderFilter, "tenderFilter must not be null");
        return nonNullWriter.writeValueAsString(buildComparablePayloadNode(snapshot(tenderFilter)));
    }

    public String buildSearchPayload(TenderFilter tenderFilter, String dateFromInstant, String dateToInstant, int pageNumber)
            throws JsonProcessingException {
        Objects.requireNonNull(tenderFilter, "tenderFilter must not be null");
        if (dateFromInstant == null || dateFromInstant.isBlank()) {
            throw new BadRequestException("dateFromInstant must not be blank");
        }
        if (dateToInstant == null || dateToInstant.isBlank()) {
            throw new BadRequestException("dateToInstant must not be blank");
        }

        ObjectNode payload = buildSearchPayloadNode(snapshot(tenderFilter));
        payload.put("DateTimeFrom", dateFromInstant);
        payload.put("DateTimeTo", dateToInstant);
        payload.put("PageNumber", pageNumber);

        return objectMapper.writeValueAsString(payload);
    }

    public FoundTenders readFoundTenders(String payload) throws JsonProcessingException {
        if (payload == null || payload.isBlank()) {
            throw new BadRequestException("found tenders payload must not be blank");
        }

        JsonNode root = objectMapper.readTree(payload);
        FoundTenders foundTenders = objectMapper.treeToValue(root, FoundTenders.class);
        JsonNode itemsNode = root.path("Items");

        ArrayList<FoundTender> items = new ArrayList<>();
        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                FoundTender tender = objectMapper.treeToValue(itemNode, FoundTender.class);
                tender.setOtherInformation(compactRawItem(itemNode.toString()));
                items.add(tender);
            }
        }

        foundTenders.setFoundTenders(items);
        return foundTenders;
    }

    public Tender readTender(String payload) throws JsonProcessingException {
        if (payload == null || payload.isBlank()) {
            throw new BadRequestException("tender payload must not be blank");
        }
        return tenderReader.readValue(payload);
    }

    public PurchaseResult readPurchaseResult(String payload) throws JsonProcessingException {
        if (payload == null || payload.isBlank()) {
            throw new BadRequestException("purchase result payload must not be blank");
        }
        return purchaseResultReader.readValue(payload);
    }

    public FavoriteTendersPage readFavoriteTendersPage(String payload) throws JsonProcessingException {
        if (payload == null || payload.isBlank()) {
            throw new BadRequestException("favorites payload must not be blank");
        }
        return favoriteTendersPageReader.readValue(payload);
    }

    public FavoriteMarkersPage readFavoriteMarkersPage(String payload) throws JsonProcessingException {
        if (payload == null || payload.isBlank()) {
            throw new BadRequestException("markers payload must not be blank");
        }
        return favoriteMarkersPageReader.readValue(payload);
    }

    public String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed to serialize JSON");
        }
    }

    private String compactRawItem(String rawItemJson) {
        if (rawItemJson == null || rawItemJson.length() <= MAX_RAW_ITEM_JSON_LENGTH) {
            return rawItemJson;
        }
        return rawItemJson.substring(0, MAX_RAW_ITEM_JSON_LENGTH);
    }

    private ObjectNode buildSearchPayloadNode(SearchArgsSnapshot snapshot) {
        ObjectNode payload = objectMapper.createObjectNode();

        putStringArray(payload, "Text", snapshot.text());
        putStringArray(payload, "Exclude", snapshot.exclude());
        putIntegerArray(payload, "Categories", snapshot.categories());
        putStringArray(payload, "IncludeInns", snapshot.includeInns());
        putStringArray(payload, "ExcludeInns", snapshot.excludeInns());
        putInstant(payload, "DateTimeFrom", snapshot.dateTimeFrom());
        putInstant(payload, "DateTimeTo", snapshot.dateTimeTo());
        putStringArray(payload, "ParticipantsInns", snapshot.participantsInns());
        putInteger(payload, "ParticipantsState", snapshot.participantsState());
        putBoolean(payload, "EnableParticipantsFromDocuments", snapshot.enableParticipantsFromDocuments());
        putStringArray(payload, "RegionIds", snapshot.regionIds());
        putIntegerArray(payload, "PurchaseStatuses", snapshot.purchaseStatuses());
        putIntegerArray(payload, "Laws", snapshot.laws());
        putIntegerArray(payload, "Procedures", snapshot.procedures());
        putIntegerArray(payload, "ElectronicPlaces", snapshot.electronicPlaces());
        putIntegerArray(payload, "CategoryIds", snapshot.categoryIds());
        putBoolean(payload, "StrictSearch", snapshot.strictSearch());
        putBoolean(payload, "Attachments", snapshot.attachments());
        putLong(payload, "MaxPriceFrom", snapshot.maxPriceFrom());
        putLong(payload, "MaxPriceTo", snapshot.maxPriceTo());
        putBoolean(payload, "MaxPriceNone", snapshot.maxPriceNone());
        putBoolean(payload, "Advance44", snapshot.advance44());
        putBoolean(payload, "Advance223", snapshot.advance223());
        putBoolean(payload, "NonAdvance", snapshot.nonAdvance());
        putInteger(payload, "Smp", snapshot.smp());
        putBoolean(payload, "AllowForeignCurrency", snapshot.allowForeignCurrency());
        putInteger(payload, "PageNumber", snapshot.pageNumber());
        putInstant(payload, "ApplicationDeadlineFrom", snapshot.applicationDeadlineFrom());
        putInstant(payload, "ApplicationDeadlineTo", snapshot.applicationDeadlineTo());

        return payload;
    }

    private ObjectNode buildComparablePayloadNode(SearchArgsSnapshot snapshot) {
        ObjectNode payload = buildSearchPayloadNode(snapshot);
        putInteger(payload, "SortOrder", snapshot.sortOrder());
        putString(payload, "ApplicationDeadlineType", snapshot.applicationDeadlineType());
        putIntegerArray(payload, "IncludedRequirementIds", snapshot.includedRequirementIds());
        putIntegerArray(payload, "ExcludedRequirementIds", snapshot.excludedRequirementIds());
        return payload;
    }

    private SearchArgsSnapshot snapshot(AddTenderFilterRequest request) {
        return new SearchArgsSnapshot(
                toStringArray(request.getText()),
                toStringArray(request.getExclude()),
                toIntegerArray(request.getCategories()),
                toStringArray(request.getIncludeInns()),
                toStringArray(request.getExcludeInns()),
                toInstant(request.getDateTimeFrom()),
                toInstant(request.getDateTimeTo()),
                toStringArray(request.getParticipantsInns()),
                request.getParticipantsState(),
                request.getEnableParticipantsFromDocuments(),
                toStringArray(request.getRegionIds()),
                toIntegerArray(request.getPurchaseStatuses()),
                toIntegerArray(request.getLaws()),
                toIntegerArray(request.getProcedures()),
                toIntegerArray(request.getElectronicPlaces()),
                toIntegerArray(request.getCategoryIds()),
                request.getStrictSearch(),
                request.getAttachments(),
                request.getMaxPriceFrom(),
                request.getMaxPriceTo(),
                request.getMaxPriceNone(),
                request.getAdvance44(),
                request.getAdvance223(),
                request.getNonAdvance(),
                request.getSmp(),
                request.getAllowForeignCurrency(),
                request.getPageNumber(),
                toInstant(request.getApplicationDeadlineFrom()),
                toInstant(request.getApplicationDeadlineTo()),
                request.getSortOrder(),
                request.getApplicationDeadlineType(),
                toIntegerArray(request.getIncludedRequirementIds()),
                toIntegerArray(request.getExcludedRequirementIds())
        );
    }

    private SearchArgsSnapshot snapshot(TenderFilter tenderFilter) {
        return new SearchArgsSnapshot(
                tenderFilter.getText(),
                tenderFilter.getExclude(),
                tenderFilter.getCategories(),
                tenderFilter.getIncludeInns(),
                tenderFilter.getExcludeInns(),
                tenderFilter.getDateTimeFrom(),
                tenderFilter.getDateTimeTo(),
                tenderFilter.getParticipantsInns(),
                tenderFilter.getParticipantsState(),
                tenderFilter.getEnableParticipantsFromDocuments(),
                tenderFilter.getRegionIds(),
                tenderFilter.getPurchaseStatuses(),
                tenderFilter.getLaws(),
                tenderFilter.getProcedures(),
                tenderFilter.getElectronicPlaces(),
                tenderFilter.getCategoryIds(),
                tenderFilter.getStrictSearch(),
                tenderFilter.getAttachments(),
                tenderFilter.getMaxPriceFrom(),
                tenderFilter.getMaxPriceTo(),
                tenderFilter.getMaxPriceNone(),
                tenderFilter.getAdvance44(),
                tenderFilter.getAdvance223(),
                tenderFilter.getNonAdvance(),
                tenderFilter.getSmp(),
                tenderFilter.getAllowForeignCurrency(),
                tenderFilter.getPageNumber(),
                tenderFilter.getApplicationDeadlineFrom(),
                tenderFilter.getApplicationDeadlineTo(),
                tenderFilter.getSortOrder(),
                tenderFilter.getApplicationDeadlineType(),
                tenderFilter.getIncludedRequirementIds(),
                tenderFilter.getExcludedRequirementIds()
        );
    }

    private void putStringArray(ObjectNode payload, String fieldName, String[] values) {
        if (values == null) {
            return;
        }
        var array = payload.putArray(fieldName);
        for (String value : values) {
            if (value != null) {
                array.add(value);
            }
        }
    }

    private void putIntegerArray(ObjectNode payload, String fieldName, Integer[] values) {
        if (values == null) {
            return;
        }
        var array = payload.putArray(fieldName);
        for (Integer value : values) {
            if (value != null) {
                array.add(value);
            }
        }
    }

    private void putBoolean(ObjectNode payload, String fieldName, Boolean value) {
        if (value != null) {
            payload.put(fieldName, value);
        }
    }

    private void putInteger(ObjectNode payload, String fieldName, Integer value) {
        if (value != null) {
            payload.put(fieldName, value);
        }
    }

    private void putLong(ObjectNode payload, String fieldName, Long value) {
        if (value != null) {
            payload.put(fieldName, value);
        }
    }

    private void putString(ObjectNode payload, String fieldName, String value) {
        if (value != null) {
            payload.put(fieldName, value);
        }
    }

    private void putInstant(ObjectNode payload, String fieldName, Instant value) {
        if (value != null) {
            payload.put(fieldName, value.toString());
        }
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

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record SearchArgsSnapshot(
            String[] text,
            String[] exclude,
            Integer[] categories,
            String[] includeInns,
            String[] excludeInns,
            Instant dateTimeFrom,
            Instant dateTimeTo,
            String[] participantsInns,
            Integer participantsState,
            Boolean enableParticipantsFromDocuments,
            String[] regionIds,
            Integer[] purchaseStatuses,
            Integer[] laws,
            Integer[] procedures,
            Integer[] electronicPlaces,
            Integer[] categoryIds,
            Boolean strictSearch,
            Boolean attachments,
            Long maxPriceFrom,
            Long maxPriceTo,
            Boolean maxPriceNone,
            Boolean advance44,
            Boolean advance223,
            Boolean nonAdvance,
            Integer smp,
            Boolean allowForeignCurrency,
            Integer pageNumber,
            Instant applicationDeadlineFrom,
            Instant applicationDeadlineTo,
            Integer sortOrder,
            String applicationDeadlineType,
            Integer[] includedRequirementIds,
            Integer[] excludedRequirementIds
    ) {
    }
}
