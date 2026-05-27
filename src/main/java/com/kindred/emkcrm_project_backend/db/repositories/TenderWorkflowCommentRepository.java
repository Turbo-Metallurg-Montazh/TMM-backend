package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflowComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenderWorkflowCommentRepository extends JpaRepository<TenderWorkflowComment, Long> {

    List<TenderWorkflowComment> findAllByWorkflowIdOrderByCreatedAtDescIdDesc(Long workflowId);

    Optional<TenderWorkflowComment> findByIdAndWorkflowId(Long id, Long workflowId);
}
