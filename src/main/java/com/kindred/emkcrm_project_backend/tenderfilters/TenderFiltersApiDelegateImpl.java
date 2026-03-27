package com.kindred.emkcrm_project_backend.tenderfilters;

import com.kindred.emkcrm_project_backend.api.TenderFiltersApiDelegate;
import com.kindred.emkcrm_project_backend.model.AddTenderFilterRequest;
import com.kindred.emkcrm_project_backend.model.ExportTendersByFilterRequest;
import com.kindred.emkcrm_project_backend.model.ExportTendersByFilterResponse;
import com.kindred.emkcrm_project_backend.model.MessageResponse;
import com.kindred.emkcrm_project_backend.services.TenderFilterExportService;
import com.kindred.emkcrm_project_backend.services.TenderFilterManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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
    public ResponseEntity<ExportTendersByFilterResponse> exportTendersByFilter(ExportTendersByFilterRequest exportTendersByFilterRequest) {
        return ResponseEntity.ok(tenderFilterExportService.exportTendersByFilter(exportTendersByFilterRequest));
    }
}
