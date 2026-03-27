package com.kindred.emkcrm_project_backend.tenderfilters;

import com.kindred.emkcrm_project_backend.api.TenderFiltersApiDelegate;
import com.kindred.emkcrm_project_backend.model.AddTenderFilterRequest;
import com.kindred.emkcrm_project_backend.model.MessageResponse;
import com.kindred.emkcrm_project_backend.services.TenderFilterManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class TenderFiltersApiDelegateImpl implements TenderFiltersApiDelegate {

    private final TenderFilterManagementService tenderFilterManagementService;

    public TenderFiltersApiDelegateImpl(TenderFilterManagementService tenderFilterManagementService) {
        this.tenderFilterManagementService = tenderFilterManagementService;
    }

    @Override
    public ResponseEntity<MessageResponse> addTenderFilter(AddTenderFilterRequest addTenderFilterRequest) {
        MessageResponse response = new MessageResponse();
        response.setMessage(tenderFilterManagementService.addTenderFilter(addTenderFilterRequest));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
