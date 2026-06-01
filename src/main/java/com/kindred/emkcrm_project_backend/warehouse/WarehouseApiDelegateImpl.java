package com.kindred.emkcrm_project_backend.warehouse;

import com.kindred.emkcrm_project_backend.api.WarehouseApiDelegate;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetCloseRequest;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetCreateRequest;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetDetailsResponse;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetHistoryResponse;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetPageResponse;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetQuantityChangeRequest;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetUpdateRequest;
import com.kindred.emkcrm_project_backend.services.IlliquidAssetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WarehouseApiDelegateImpl implements WarehouseApiDelegate {

    private final IlliquidAssetService illiquidAssetService;

    public WarehouseApiDelegateImpl(IlliquidAssetService illiquidAssetService) {
        this.illiquidAssetService = illiquidAssetService;
    }

    @Override
    public ResponseEntity<IlliquidAssetPageResponse> listIlliquidAssets(
            String status,
            String q,
            Integer page,
            Integer size,
            String sort,
            String direction
    ) {
        return ResponseEntity.ok(illiquidAssetService.list(status, q, page, size, sort, direction));
    }

    @Override
    public ResponseEntity<IlliquidAssetDetailsResponse> createIlliquidAsset(IlliquidAssetCreateRequest illiquidAssetCreateRequest) {
        return new ResponseEntity<>(illiquidAssetService.create(illiquidAssetCreateRequest), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<IlliquidAssetDetailsResponse> getIlliquidAssetById(Long id) {
        return ResponseEntity.ok(illiquidAssetService.getById(id));
    }

    @Override
    public ResponseEntity<IlliquidAssetDetailsResponse> updateIlliquidAsset(
            Long id,
            IlliquidAssetUpdateRequest illiquidAssetUpdateRequest
    ) {
        return ResponseEntity.ok(illiquidAssetService.update(id, illiquidAssetUpdateRequest));
    }

    @Override
    public ResponseEntity<IlliquidAssetDetailsResponse> changeIlliquidAssetQuantity(
            Long id,
            IlliquidAssetQuantityChangeRequest illiquidAssetQuantityChangeRequest
    ) {
        return ResponseEntity.ok(illiquidAssetService.changeQuantity(id, illiquidAssetQuantityChangeRequest));
    }

    @Override
    public ResponseEntity<IlliquidAssetDetailsResponse> closeIlliquidAsset(
            Long id,
            IlliquidAssetCloseRequest illiquidAssetCloseRequest
    ) {
        return ResponseEntity.ok(illiquidAssetService.close(id, illiquidAssetCloseRequest));
    }

    @Override
    public ResponseEntity<IlliquidAssetDetailsResponse> deleteIlliquidAsset(
            Long id,
            IlliquidAssetCloseRequest illiquidAssetCloseRequest
    ) {
        return ResponseEntity.ok(illiquidAssetService.close(id, illiquidAssetCloseRequest));
    }

    @Override
    public ResponseEntity<List<IlliquidAssetHistoryResponse>> listIlliquidAssetHistory(Long id) {
        return ResponseEntity.ok(illiquidAssetService.listHistory(id));
    }
}
