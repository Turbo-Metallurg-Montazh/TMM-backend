package com.kindred.emkcrm_project_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenderFilterUrlParserTest {

    private TenderFilterUrlParser parser;

    @BeforeEach
    void setUp() {
        parser = new TenderFilterUrlParser();
    }

    @Test
    void parseMapsExtendedKonturWebFilterFields() {
        String filterUrl = "https://zakupki.kontur.ru/category-region/uslugi-marketingovyx-reklamnyx-i-pr-agentstv/"
                + "severo-zapadnyj-federalnyj-okrug?q.AllowForeignCurrency=True"
                + "&q.Exclude=%D0%B0%D0%BC%D0%B2%D0%BE%D1%82%D0%BC%D1%88%D0%B4%D0%BB+%D0%B0%D1%82%D0%BC%D0%BE%D0%B0%D0%BF%D1%82"
                + "&q.ExcludedRequirementIds=3"
                + "&q.IncludedRequirementIds=1%2C2%2C4%2C5"
                + "&q.Inns=2312159262"
                + "&q.ISCategoryIds=1%2C2%2C3"
                + "&q.Laws=0%2C2%2C5%2C6"
                + "&q.MaxPrice.From=4365278"
                + "&q.MaxPrice.None=True"
                + "&q.MaxPrice.To=432587239"
                + "&q.Procedures=0%2C1%2C3%2C99"
                + "&q.PublishDateFrom=04.08.2025"
                + "&q.PublishDateTo=04.07.2026"
                + "&q.PurchaseStatuses=1%2C4%2C5"
                + "&q.RegionIds=ccddbc38-f962-4167-b62a-79776c9e853d"
                + "&q.Smp=Only"
                + "&q.Text=%D0%B0%D0%BF%D1%80%D0%BA%D1%8B%D0%BE%D0%B4%D0%B8%D1%82%D0%BE%D1%86%D0%B4%D1%84%D0%BB%D0%B0%D0%BE%D1%82%D0%B4%D0%BE%D0%BF";

        TenderFilterUrlParser.ParsedTenderFilter parsedFilter = parser.parse(filterUrl);

        assertThat(parsedFilter.rawText()).isEqualTo("апркыодитоцдфлаотдоп");
        assertThat(parsedFilter.request().getText()).containsExactly("апркыодитоцдфлаотдоп");
        assertThat(parsedFilter.request().getExclude()).containsExactly("амвотмшдл атмоапт");
        assertThat(parsedFilter.request().getAllowForeignCurrency()).isTrue();
        assertThat(parsedFilter.request().getIncludeInns()).containsExactly("2312159262");
        assertThat(parsedFilter.request().getExcludeInns()).isNull();
        assertThat(parsedFilter.request().getCategoryIds()).containsExactly(1, 2, 3);
        assertThat(parsedFilter.request().getLaws()).containsExactly(0, 2, 5, 6);
        assertThat(parsedFilter.request().getMaxPriceFrom()).isEqualTo(4_365_278L);
        assertThat(parsedFilter.request().getMaxPriceTo()).isEqualTo(432_587_239L);
        assertThat(parsedFilter.request().getMaxPriceNone()).isTrue();
        assertThat(parsedFilter.request().getProcedures()).containsExactly(0, 1, 3, 99);
        assertThat(parsedFilter.request().getPurchaseStatuses()).containsExactly(1, 4, 5);
        assertThat(parsedFilter.request().getSmp()).isEqualTo(1);
        assertThat(parsedFilter.request().getIncludedRequirementIds()).containsExactly(1, 2, 4, 5);
        assertThat(parsedFilter.request().getExcludedRequirementIds()).containsExactly(3);
        assertThat(parsedFilter.request().getRegionIds()).containsExactly(UUID.fromString("ccddbc38-f962-4167-b62a-79776c9e853d"));
        assertThat(parsedFilter.request().getDateTimeFrom())
                .isEqualTo(OffsetDateTime.of(2025, 8, 4, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(parsedFilter.request().getDateTimeTo())
                .isEqualTo(OffsetDateTime.of(2026, 7, 4, 23, 59, 59, 999_999_999, ZoneOffset.UTC));
    }

    @Test
    void parseLeavesMissingFieldsNullAndMapsSortOrderAndDeadlineType() {
        String filterUrl = "https://zakupki.kontur.ru/Grid?q.ApplicationDeadlineType=All"
                + "&q.ExcludeInns=false"
                + "&q.ISCategoryIds="
                + "&q.Inns=2312159262"
                + "&q.Laws=0%2C1%2C4%2C2%2C6"
                + "&q.PurchaseStatuses=1%2C2%2C3%2C4%2C5"
                + "&q.Smp=All"
                + "&q.SortOrder=2"
                + "&q.Text=test+filter";

        TenderFilterUrlParser.ParsedTenderFilter parsedFilter = parser.parse(filterUrl);

        assertThat(parsedFilter.request().getText()).containsExactly("test filter");
        assertThat(parsedFilter.request().getIncludeInns()).containsExactly("2312159262");
        assertThat(parsedFilter.request().getExcludeInns()).isNull();
        assertThat(parsedFilter.request().getCategoryIds()).isNull();
        assertThat(parsedFilter.request().getDateTimeFrom()).isNull();
        assertThat(parsedFilter.request().getDateTimeTo()).isNull();
        assertThat(parsedFilter.request().getApplicationDeadlineType()).isEqualTo("All");
        assertThat(parsedFilter.request().getSortOrder()).isEqualTo(2);
        assertThat(parsedFilter.request().getLaws()).containsExactly(0, 1, 4, 2, 6);
        assertThat(parsedFilter.request().getPurchaseStatuses()).containsExactly(1, 2, 3, 4, 5);
        assertThat(parsedFilter.request().getSmp()).isEqualTo(0);
    }

    @Test
    void resolveFilterNameUsesProvidedNameTextPrefixOrFallback() {
        assertThat(parser.resolveFilterName("  Мой фильтр  ", "ignored"))
                .isEqualTo("Мой фильтр");
        assertThat(parser.resolveFilterName(" ", "12345678901234567890123456789"))
                .isEqualTo("123456789012345678901234");
        assertThat(parser.resolveFilterName(null, null))
                .isEqualTo("Без названия");
        assertThat(parser.resolveFilterName("", "короткий"))
                .isEqualTo("короткий");
    }
}
