package com.kindred.emkcrm_project_backend.services;

import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.model.AddTenderFilterRequest;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class TenderFilterUrlParser {

    private static final int GENERATED_NAME_LENGTH = 24;
    private static final String DEFAULT_GENERATED_NAME = "Без названия";
    private static final DateTimeFormatter WEB_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern TEXT_SPLIT_PATTERN = Pattern.compile("[,\\r\\n]+");

    public ParsedTenderFilter parse(String rawFilter) {
        Map<String, String> params = parseQueryParameters(rawFilter);
        AddTenderFilterRequest request = new AddTenderFilterRequest();
        resetDefaultCollections(request);

        String rawText = normalizeText(param(params, "Text"));
        request.setText(parseTextValues(rawText));
        request.setExclude(parseTextValues(param(params, "Exclude")));
        request.setCategories(parseIntegerList(param(params, "Categories")));
        request.setParticipantsInns(parseStringList(param(params, "ParticipantsInns")));
        request.setParticipantsState(parseInteger(param(params, "ParticipantsState")));
        request.setEnableParticipantsFromDocuments(parseBoolean(param(params, "EnableParticipantsFromDocuments")));
        request.setRegionIds(parseUuidList(param(params, "RegionIds")));
        request.setPurchaseStatuses(parseIntegerList(param(params, "PurchaseStatuses")));
        request.setLaws(parseIntegerList(param(params, "Laws")));
        request.setProcedures(parseIntegerList(param(params, "Procedures")));
        request.setElectronicPlaces(parseIntegerList(param(params, "ElectronicPlaces")));
        request.setCategoryIds(firstNonNullIntegerList(
                parseIntegerList(param(params, "ISCategoryIds")),
                parseIntegerList(param(params, "CategoryIds"))
        ));
        request.setStrictSearch(parseBoolean(param(params, "StrictSearch")));
        request.setAttachments(parseBoolean(param(params, "Attachments")));
        request.setMaxPriceFrom(parseLong(param(params, "MaxPrice.From")));
        request.setMaxPriceTo(parseLong(param(params, "MaxPrice.To")));
        request.setMaxPriceNone(parseBoolean(param(params, "MaxPrice.None")));
        request.setAdvance44(parseBoolean(param(params, "Advance44")));
        request.setAdvance223(parseBoolean(param(params, "Advance223")));
        request.setNonAdvance(parseBoolean(param(params, "NonAdvance")));
        request.setSmp(parseSmp(param(params, "Smp")));
        request.setAllowForeignCurrency(parseBoolean(param(params, "AllowForeignCurrency")));
        request.setPageNumber(parseInteger(param(params, "PageNumber")));
        request.setSortOrder(parseInteger(param(params, "SortOrder")));
        request.setDateTimeFrom(firstNonNullDateTime(
                parseDateTime(param(params, "DateTimeFrom"), false),
                parseDateTime(param(params, "PublishDateFrom"), false)
        ));
        request.setDateTimeTo(firstNonNullDateTime(
                parseDateTime(param(params, "DateTimeTo"), true),
                parseDateTime(param(params, "PublishDateTo"), true)
        ));
        request.setApplicationDeadlineFrom(parseDateTime(param(params, "ApplicationDeadlineFrom"), false));
        request.setApplicationDeadlineTo(parseDateTime(param(params, "ApplicationDeadlineTo"), true));
        request.setApplicationDeadlineType(normalizeText(param(params, "ApplicationDeadlineType")));
        request.setIncludedRequirementIds(parseIntegerList(param(params, "IncludedRequirementIds")));
        request.setExcludedRequirementIds(parseIntegerList(param(params, "ExcludedRequirementIds")));

        applyInnFilters(request, params);
        return new ParsedTenderFilter(request, rawText);
    }

    public String resolveFilterName(String requestedName, String rawText) {
        String normalizedRequestedName = normalizeText(requestedName);
        if (normalizedRequestedName != null) {
            return normalizedRequestedName;
        }

        if (rawText == null) {
            return DEFAULT_GENERATED_NAME;
        }

        return rawText.length() <= GENERATED_NAME_LENGTH
                ? rawText
                : rawText.substring(0, GENERATED_NAME_LENGTH);
    }

    private void applyInnFilters(AddTenderFilterRequest request, Map<String, String> params) {
        List<String> inns = parseStringList(param(params, "Inns"));
        String excludeInnsValue = param(params, "ExcludeInns");

        if (inns != null) {
            Boolean excludeInnsFlag = tryParseBoolean(excludeInnsValue);
            if (Boolean.TRUE.equals(excludeInnsFlag)) {
                request.setExcludeInns(inns);
            } else {
                request.setIncludeInns(inns);
            }
            return;
        }

        request.setIncludeInns(parseStringList(param(params, "IncludeInns")));
        if (!isBooleanToken(excludeInnsValue)) {
            request.setExcludeInns(parseStringList(excludeInnsValue));
        }
    }

    private void resetDefaultCollections(AddTenderFilterRequest request) {
        request.setText(null);
        request.setExclude(null);
        request.setCategories(null);
        request.setIncludeInns(null);
        request.setExcludeInns(null);
        request.setParticipantsInns(null);
        request.setRegionIds(null);
        request.setPurchaseStatuses(null);
        request.setLaws(null);
        request.setProcedures(null);
        request.setElectronicPlaces(null);
        request.setCategoryIds(null);
        request.setIncludedRequirementIds(null);
        request.setExcludedRequirementIds(null);
    }

    private Map<String, String> parseQueryParameters(String rawFilter) {
        if (rawFilter == null || rawFilter.isBlank()) {
            throw new BadRequestException("filter is required");
        }

        String query = rawFilter.trim();
        int questionMarkIndex = query.indexOf('?');
        if (questionMarkIndex >= 0) {
            query = query.substring(questionMarkIndex + 1);
        }

        int fragmentIndex = query.indexOf('#');
        if (fragmentIndex >= 0) {
            query = query.substring(0, fragmentIndex);
        }

        if (query.isBlank()) {
            throw new BadRequestException("filter must contain query parameters");
        }

        Map<String, String> params = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            if (part.isBlank()) {
                continue;
            }

            int separatorIndex = part.indexOf('=');
            String rawKey = separatorIndex >= 0 ? part.substring(0, separatorIndex) : part;
            String rawValue = separatorIndex >= 0 ? part.substring(separatorIndex + 1) : "";
            params.put(
                    decode(rawKey),
                    decode(rawValue)
            );
        }
        return params;
    }

    private String param(Map<String, String> params, String name) {
        String direct = normalizeText(params.get(name));
        if (direct != null) {
            return direct;
        }
        return normalizeText(params.get("q." + name));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private List<String> parseTextValues(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        List<String> items = new ArrayList<>();
        for (String item : TEXT_SPLIT_PATTERN.split(normalizedValue)) {
            String normalizedItem = normalizeText(item);
            if (normalizedItem != null) {
                items.add(normalizedItem);
            }
        }
        return items.isEmpty() ? null : items;
    }

    private List<String> parseStringList(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        List<String> items = new ArrayList<>();
        for (String item : normalizedValue.split(",")) {
            String normalizedItem = normalizeText(item);
            if (normalizedItem != null) {
                items.add(normalizedItem);
            }
        }
        return items.isEmpty() ? null : items;
    }

    private List<Integer> parseIntegerList(String value) {
        List<String> items = parseStringList(value);
        if (items == null) {
            return null;
        }

        List<Integer> parsedItems = new ArrayList<>(items.size());
        for (String item : items) {
            parsedItems.add(parseInteger(item));
        }
        return parsedItems;
    }

    private List<UUID> parseUuidList(String value) {
        List<String> items = parseStringList(value);
        if (items == null) {
            return null;
        }

        List<UUID> parsedItems = new ArrayList<>(items.size());
        for (String item : items) {
            try {
                parsedItems.add(UUID.fromString(item));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid UUID value: " + item);
            }
        }
        return parsedItems;
    }

    private Integer parseInteger(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }
        try {
            return Integer.valueOf(normalizedValue);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid integer value: " + normalizedValue);
        }
    }

    private Long parseLong(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }
        try {
            return Long.valueOf(normalizedValue);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid long value: " + normalizedValue);
        }
    }

    private Boolean parseBoolean(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        Boolean parsedValue = tryParseBoolean(normalizedValue);
        if (parsedValue != null) {
            return parsedValue;
        }
        throw new BadRequestException("Invalid boolean value: " + normalizedValue);
    }

    private boolean isBooleanToken(String value) {
        return tryParseBoolean(value) != null;
    }

    private Boolean tryParseBoolean(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        if ("true".equalsIgnoreCase(normalizedValue)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalizedValue)) {
            return false;
        }
        return null;
    }

    private Integer parseSmp(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        return switch (normalizedValue.toLowerCase(Locale.ROOT)) {
            case "all" -> 0;
            case "only" -> 1;
            case "no" -> 2;
            default -> parseInteger(normalizedValue);
        };
    }

    private OffsetDateTime parseDateTime(String value, boolean endOfDay) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        try {
            return OffsetDateTime.parse(normalizedValue);
        } catch (DateTimeParseException ignored) {
            // Try web date format below.
        }

        try {
            LocalDate date = LocalDate.parse(normalizedValue, WEB_DATE_FORMATTER);
            return (endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay())
                    .atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid date value: " + normalizedValue);
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<Integer> firstNonNullIntegerList(List<Integer> first, List<Integer> second) {
        return first != null ? first : second;
    }

    private OffsetDateTime firstNonNullDateTime(OffsetDateTime first, OffsetDateTime second) {
        return first != null ? first : second;
    }

    public record ParsedTenderFilter(
            AddTenderFilterRequest request,
            String rawText
    ) {
    }
}
