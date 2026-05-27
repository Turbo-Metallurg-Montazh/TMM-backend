package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflowStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenderWorkflowStatusHistoryRepository extends JpaRepository<TenderWorkflowStatusHistory, Long> {

    List<TenderWorkflowStatusHistory> findAllByWorkflowIdOrderByCreatedAtDescIdDesc(Long workflowId);
}
