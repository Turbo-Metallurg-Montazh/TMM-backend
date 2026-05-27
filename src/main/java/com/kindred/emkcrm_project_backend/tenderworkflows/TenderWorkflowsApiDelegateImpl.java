package com.kindred.emkcrm_project_backend.tenderworkflows;

import com.kindred.emkcrm_project_backend.api.TenderWorkflowsApiDelegate;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyCheckResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyDetailsResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartySummaryResponse;
import com.kindred.emkcrm_project_backend.model.MessageResponse;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowCommentCreateRequest;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowCommentResponse;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowCommentUpdateRequest;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowCounterpartyCheckRequest;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowCounterpartyLinkRequest;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowCreateRequest;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowDetailsResponse;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowFileCreateRequest;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowFileResponse;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowPageResponse;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowStatusChangeRequest;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowStatusHistoryResponse;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowUpdateRequest;
import com.kindred.emkcrm_project_backend.services.TenderWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class TenderWorkflowsApiDelegateImpl implements TenderWorkflowsApiDelegate {

    private final TenderWorkflowService tenderWorkflowService;

    public TenderWorkflowsApiDelegateImpl(TenderWorkflowService tenderWorkflowService) {
        this.tenderWorkflowService = tenderWorkflowService;
    }

    @Override
    public ResponseEntity<TenderWorkflowPageResponse> listTenderWorkflows(
            String status,
            String priority,
            Long responsibleManagerId,
            Long supplyUserId,
            Long lawyerUserId,
            Long purchaseTenderId,
            String notificationNumber,
            String inn,
            OffsetDateTime dateFrom,
            OffsetDateTime dateTo,
            Integer page,
            Integer size,
            String sort,
            String direction
    ) {
        return ResponseEntity.ok(tenderWorkflowService.list(
                status,
                priority,
                responsibleManagerId,
                supplyUserId,
                lawyerUserId,
                purchaseTenderId,
                notificationNumber,
                inn,
                dateFrom,
                dateTo,
                page,
                size,
                sort,
                direction
        ));
    }

    @Override
    public ResponseEntity<TenderWorkflowDetailsResponse> createTenderWorkflow(TenderWorkflowCreateRequest tenderWorkflowCreateRequest) {
        return new ResponseEntity<>(tenderWorkflowService.create(tenderWorkflowCreateRequest), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<TenderWorkflowDetailsResponse> getTenderWorkflowById(Long id) {
        return ResponseEntity.ok(tenderWorkflowService.getById(id));
    }

    @Override
    public ResponseEntity<TenderWorkflowDetailsResponse> updateTenderWorkflow(
            Long id,
            TenderWorkflowUpdateRequest tenderWorkflowUpdateRequest
    ) {
        return ResponseEntity.ok(tenderWorkflowService.update(id, tenderWorkflowUpdateRequest));
    }

    @Override
    public ResponseEntity<TenderWorkflowDetailsResponse> changeTenderWorkflowStatus(
            Long id,
            TenderWorkflowStatusChangeRequest tenderWorkflowStatusChangeRequest
    ) {
        return ResponseEntity.ok(tenderWorkflowService.changeStatus(id, tenderWorkflowStatusChangeRequest));
    }

    @Override
    public ResponseEntity<List<TenderWorkflowStatusHistoryResponse>> listTenderWorkflowHistory(Long id) {
        return ResponseEntity.ok(tenderWorkflowService.listHistory(id));
    }

    @Override
    public ResponseEntity<List<TenderWorkflowCommentResponse>> listTenderWorkflowComments(Long id) {
        return ResponseEntity.ok(tenderWorkflowService.listComments(id));
    }

    @Override
    public ResponseEntity<TenderWorkflowCommentResponse> addTenderWorkflowComment(
            Long id,
            TenderWorkflowCommentCreateRequest tenderWorkflowCommentCreateRequest
    ) {
        return new ResponseEntity<>(
                tenderWorkflowService.addComment(id, tenderWorkflowCommentCreateRequest),
                HttpStatus.CREATED
        );
    }

    @Override
    public ResponseEntity<TenderWorkflowCommentResponse> updateTenderWorkflowComment(
            Long id,
            Long commentId,
            TenderWorkflowCommentUpdateRequest tenderWorkflowCommentUpdateRequest
    ) {
        return ResponseEntity.ok(tenderWorkflowService.updateComment(id, commentId, tenderWorkflowCommentUpdateRequest));
    }

    @Override
    public ResponseEntity<MessageResponse> deleteTenderWorkflowComment(Long id, Long commentId) {
        MessageResponse response = new MessageResponse();
        response.setMessage(tenderWorkflowService.deleteComment(id, commentId));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<TenderWorkflowFileResponse>> listTenderWorkflowFiles(Long id) {
        return ResponseEntity.ok(tenderWorkflowService.listFiles(id));
    }

    @Override
    public ResponseEntity<TenderWorkflowFileResponse> addTenderWorkflowFile(
            Long id,
            TenderWorkflowFileCreateRequest tenderWorkflowFileCreateRequest
    ) {
        return new ResponseEntity<>(
                tenderWorkflowService.addFile(id, tenderWorkflowFileCreateRequest),
                HttpStatus.CREATED
        );
    }

    @Override
    public ResponseEntity<MessageResponse> deleteTenderWorkflowFile(Long id, Long fileId) {
        MessageResponse response = new MessageResponse();
        response.setMessage(tenderWorkflowService.deleteFile(id, fileId));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<LegalCounterpartySummaryResponse>> listTenderWorkflowCounterparties(Long id) {
        return ResponseEntity.ok(tenderWorkflowService.listCounterparties(id));
    }

    @Override
    public ResponseEntity<LegalCounterpartyDetailsResponse> linkTenderWorkflowCounterparty(
            Long id,
            TenderWorkflowCounterpartyLinkRequest tenderWorkflowCounterpartyLinkRequest
    ) {
        return ResponseEntity.ok(tenderWorkflowService.linkCounterparty(id, tenderWorkflowCounterpartyLinkRequest));
    }

    @Override
    public ResponseEntity<LegalCounterpartyCheckResponse> createTenderWorkflowCounterpartyCheck(
            Long id,
            TenderWorkflowCounterpartyCheckRequest tenderWorkflowCounterpartyCheckRequest
    ) {
        return new ResponseEntity<>(
                tenderWorkflowService.createCounterpartyCheck(id, tenderWorkflowCounterpartyCheckRequest),
                HttpStatus.CREATED
        );
    }
}
