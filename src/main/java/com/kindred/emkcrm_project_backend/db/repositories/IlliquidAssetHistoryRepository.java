package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.warehouse.IlliquidAssetHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IlliquidAssetHistoryRepository extends JpaRepository<IlliquidAssetHistory, Long> {

    List<IlliquidAssetHistory> findAllByAssetIdOrderByCreatedAtDescIdDesc(Long assetId);
}
