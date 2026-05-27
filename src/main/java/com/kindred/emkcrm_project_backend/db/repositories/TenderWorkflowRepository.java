package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TenderWorkflowRepository extends JpaRepository<TenderWorkflow, Long>, JpaSpecificationExecutor<TenderWorkflow> {

    boolean existsByPurchaseTenderDbId(Long purchaseTenderId);

    Optional<TenderWorkflow> findByPurchaseTenderDbId(Long purchaseTenderId);
}
