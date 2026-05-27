package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflowFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenderWorkflowFileRepository extends JpaRepository<TenderWorkflowFile, Long> {

    List<TenderWorkflowFile> findAllByWorkflowIdOrderByCreatedAtDescIdDesc(Long workflowId);

    Optional<TenderWorkflowFile> findByIdAndWorkflowId(Long id, Long workflowId);
}
