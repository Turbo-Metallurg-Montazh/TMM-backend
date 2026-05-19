package com.kindred.emkcrm_project_backend.legal;

import com.kindred.emkcrm_project_backend.api.LegalCounterpartiesApiDelegate;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyAssessmentResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyCheckCreateRequest;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyCheckResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyDetailsResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyIncidentCreateRequest;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyIncidentResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyPageResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyUpsertRequest;
import com.kindred.emkcrm_project_backend.model.MessageResponse;
import com.kindred.emkcrm_project_backend.services.LegalCounterpartyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LegalCounterpartiesApiDelegateImpl implements LegalCounterpartiesApiDelegate {

    private final LegalCounterpartyService legalCounterpartyService;

    public LegalCounterpartiesApiDelegateImpl(LegalCounterpartyService legalCounterpartyService) {
        this.legalCounterpartyService = legalCounterpartyService;
    }

    @Override
    public ResponseEntity<LegalCounterpartyCheckResponse> addLegalCounterpartyCheck(
            Long counterpartyId,
            LegalCounterpartyCheckCreateRequest legalCounterpartyCheckCreateRequest
    ) {
        return new ResponseEntity<>(
                legalCounterpartyService.addCheck(counterpartyId, legalCounterpartyCheckCreateRequest),
                HttpStatus.CREATED
        );
    }

    @Override
    public ResponseEntity<LegalCounterpartyIncidentResponse> addLegalCounterpartyIncident(
            Long counterpartyId,
            LegalCounterpartyIncidentCreateRequest legalCounterpartyIncidentCreateRequest
    ) {
        return new ResponseEntity<>(
                legalCounterpartyService.addIncident(counterpartyId, legalCounterpartyIncidentCreateRequest),
                HttpStatus.CREATED
        );
    }

    @Override
    public ResponseEntity<LegalCounterpartyDetailsResponse> createLegalCounterparty(
            LegalCounterpartyUpsertRequest legalCounterpartyUpsertRequest
    ) {
        return new ResponseEntity<>(
                legalCounterpartyService.create(legalCounterpartyUpsertRequest),
                HttpStatus.CREATED
        );
    }

    @Override
    public ResponseEntity<MessageResponse> deleteLegalCounterparty(Long counterpartyId) {
        MessageResponse response = new MessageResponse();
        response.setMessage(legalCounterpartyService.delete(counterpartyId));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<LegalCounterpartyDetailsResponse> getLegalCounterpartyById(Long counterpartyId) {
        return ResponseEntity.ok(legalCounterpartyService.getById(counterpartyId));
    }

    @Override
    public ResponseEntity<LegalCounterpartyAssessmentResponse> getLegalCounterpartyAssessment(String inn) {
        return ResponseEntity.ok(legalCounterpartyService.getAssessment(inn));
    }

    @Override
    public ResponseEntity<List<LegalCounterpartyCheckResponse>> listLegalCounterpartyChecks(Long counterpartyId) {
        return ResponseEntity.ok(legalCounterpartyService.listChecks(counterpartyId));
    }

    @Override
    public ResponseEntity<List<LegalCounterpartyIncidentResponse>> listLegalCounterpartyIncidents(Long counterpartyId) {
        return ResponseEntity.ok(legalCounterpartyService.listIncidents(counterpartyId));
    }

    @Override
    public ResponseEntity<LegalCounterpartyPageResponse> searchLegalCounterparties(
            String query,
            String registryType,
            String riskLevel,
            Boolean workProhibited,
            Integer page,
            Integer size
    ) {
        return ResponseEntity.ok(legalCounterpartyService.search(query, registryType, riskLevel, workProhibited, page, size));
    }

    @Override
    public ResponseEntity<LegalCounterpartyDetailsResponse> updateLegalCounterparty(
            Long counterpartyId,
            LegalCounterpartyUpsertRequest legalCounterpartyUpsertRequest
    ) {
        return ResponseEntity.ok(legalCounterpartyService.update(counterpartyId, legalCounterpartyUpsertRequest));
    }
}
