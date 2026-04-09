package com.kindred.emkcrm_project_backend.tenderfilters;

import com.kindred.emkcrm_project_backend.api.TenderFiltersApiDelegate;
import com.kindred.emkcrm_project_backend.model.AddTenderFilterRequest;
import com.kindred.emkcrm_project_backend.model.ExportTendersByFilterRequest;
import com.kindred.emkcrm_project_backend.model.ExportTendersByFilterResponse;
import com.kindred.emkcrm_project_backend.model.MessageResponse;
import com.kindred.emkcrm_project_backend.model.ParseTenderFilterRequest;
import com.kindred.emkcrm_project_backend.model.ParseTenderFilterResponse;
import com.kindred.emkcrm_project_backend.model.TenderFilterDetailsResponse;
import com.kindred.emkcrm_project_backend.model.TenderFilterSummaryResponse;
import com.kindred.emkcrm_project_backend.services.TenderFilterExportService;
import com.kindred.emkcrm_project_backend.services.TenderFilterManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TenderFiltersApiDelegateImpl implements TenderFiltersApiDelegate {

    private final TenderFilterManagementService tenderFilterManagementService;
    private final TenderFilterExportService tenderFilterExportService;

    public TenderFiltersApiDelegateImpl(
            TenderFilterManagementService tenderFilterManagementService,
            TenderFilterExportService tenderFilterExportService
    ) {
        this.tenderFilterManagementService = tenderFilterManagementService;
        this.tenderFilterExportService = tenderFilterExportService;
    }

    @Override
    public ResponseEntity<MessageResponse> addTenderFilter(AddTenderFilterRequest addTenderFilterRequest) {
        MessageResponse response = new MessageResponse();
        response.setMessage(tenderFilterManagementService.addTenderFilter(addTenderFilterRequest));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ParseTenderFilterResponse> parseTenderFilter(ParseTenderFilterRequest parseTenderFilterRequest) {
        ParseTenderFilterResponse response = new ParseTenderFilterResponse();
        response.setName(tenderFilterManagementService.parseTenderFilter(parseTenderFilterRequest));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<List<TenderFilterSummaryResponse>> listTenderFilters() {
        return ResponseEntity.ok(tenderFilterManagementService.listTenderFilters());
    }

    @Override
    public ResponseEntity<TenderFilterDetailsResponse> getTenderFilterByName(String filterName) {
        return ResponseEntity.ok(tenderFilterManagementService.getTenderFilterByName(filterName));
    }

    @Override
    public ResponseEntity<TenderFilterDetailsResponse> updateTenderFilterByName(String filterName, AddTenderFilterRequest addTenderFilterRequest) {
        return ResponseEntity.ok(tenderFilterManagementService.updateTenderFilterByName(filterName, addTenderFilterRequest));
    }

    @Override
    public ResponseEntity<MessageResponse> deleteTenderFilterByName(String filterName) {
        MessageResponse response = new MessageResponse();
        response.setMessage(tenderFilterManagementService.deleteTenderFilterByName(filterName));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ExportTendersByFilterResponse> exportTendersByFilter(ExportTendersByFilterRequest exportTendersByFilterRequest) {
        return ResponseEntity.ok(tenderFilterExportService.exportTendersByFilter(exportTendersByFilterRequest));
    }
}
