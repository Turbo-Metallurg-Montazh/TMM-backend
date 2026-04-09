package com.kindred.emkcrm_project_backend.services;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TenderFilterExportServiceTest {

    @Test
    void resolveSearchRangeUsesCurrentTimeAndOneYearFallbacks() {
        Instant fixedNow = Instant.parse("2026-04-09T07:00:00Z");
        TenderFilterExportService service = new TenderFilterExportService(
                null,
                null,
                Clock.fixed(fixedNow, ZoneOffset.UTC)
        );

        TenderFilterExportService.SearchRange range = service.resolveSearchRange(null, null);

        assertThat(range.dateTo()).isEqualTo(fixedNow);
        assertThat(range.dateFrom()).isEqualTo(Instant.parse("2025-04-09T07:00:00Z"));
    }

    @Test
    void resolveSearchRangeUsesResolvedDateToAsBaseForMissingDateFrom() {
        Instant dateTo = Instant.parse("2026-01-15T10:30:00Z");
        TenderFilterExportService service = new TenderFilterExportService(
                null,
                null,
                Clock.fixed(Instant.parse("2026-04-09T07:00:00Z"), ZoneOffset.UTC)
        );

        TenderFilterExportService.SearchRange range = service.resolveSearchRange(null, dateTo);

        assertThat(range.dateTo()).isEqualTo(dateTo);
        assertThat(range.dateFrom()).isEqualTo(Instant.parse("2025-01-15T10:30:00Z"));
    }
}
