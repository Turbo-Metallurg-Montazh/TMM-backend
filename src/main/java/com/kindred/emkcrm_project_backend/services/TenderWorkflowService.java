package com.kindred.emkcrm_project_backend.services;

import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterparty;
import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterpartyTenderLink;
import com.kindred.emkcrm_project_backend.db.entities.tenderEntity.Tender;
import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflow;
import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflowComment;
import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflowCommentType;
import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflowFile;
import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflowPriority;
import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflowStatus;
import com.kindred.emkcrm_project_backend.db.entities.workflow.TenderWorkflowStatusHistory;
import com.kindred.emkcrm_project_backend.db.repositories.LegalCounterpartyRepository;
import com.kindred.emkcrm_project_backend.db.repositories.LegalCounterpartyTenderLinkRepository;
import com.kindred.emkcrm_project_backend.db.repositories.TenderRepository;
import com.kindred.emkcrm_project_backend.db.repositories.TenderWorkflowCommentRepository;
import com.kindred.emkcrm_project_backend.db.repositories.TenderWorkflowFileRepository;
import com.kindred.emkcrm_project_backend.db.repositories.TenderWorkflowRepository;
import com.kindred.emkcrm_project_backend.db.repositories.TenderWorkflowStatusHistoryRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.exception.NotFoundException;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyCheckResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyDetailsResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartySummaryResponse;
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
import com.kindred.emkcrm_project_backend.model.TenderWorkflowSummaryResponse;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowTenderSummaryResponse;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowUpdateRequest;
import com.kindred.emkcrm_project_backend.model.TenderWorkflowUserResponse;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class TenderWorkflowService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Map<TenderWorkflowStatus, Set<TenderWorkflowStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(TenderWorkflowStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.NEW, Set.of(
                TenderWorkflowStatus.PROFILE_REVIEW,
                TenderWorkflowStatus.REJECTED
        ));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.PROFILE_REVIEW, Set.of(
                TenderWorkflowStatus.FEASIBILITY_REVIEW,
                TenderWorkflowStatus.REJECTED
        ));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.FEASIBILITY_REVIEW, Set.of(
                TenderWorkflowStatus.CONTRACTOR_CHECK,
                TenderWorkflowStatus.PRICE_CALCULATION,
                TenderWorkflowStatus.REJECTED
        ));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.CONTRACTOR_CHECK, Set.of(
                TenderWorkflowStatus.PRICE_CALCULATION,
                TenderWorkflowStatus.REJECTED
        ));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.PRICE_CALCULATION, Set.of(
                TenderWorkflowStatus.APPROVAL,
                TenderWorkflowStatus.REJECTED
        ));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.APPROVAL, Set.of(
                TenderWorkflowStatus.COMMERCIAL_PROPOSAL_PREPARATION,
                TenderWorkflowStatus.REJECTED
        ));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.COMMERCIAL_PROPOSAL_PREPARATION, Set.of(
                TenderWorkflowStatus.READY_FOR_BIDDING,
                TenderWorkflowStatus.REJECTED
        ));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.READY_FOR_BIDDING, Set.of(
                TenderWorkflowStatus.BIDDING,
                TenderWorkflowStatus.REJECTED
        ));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.BIDDING, Set.of(
                TenderWorkflowStatus.CONTRACT_EXECUTION,
                TenderWorkflowStatus.LOST
        ));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.CONTRACT_EXECUTION, Set.of(TenderWorkflowStatus.WAITING_PAYMENT));
        ALLOWED_TRANSITIONS.put(TenderWorkflowStatus.WAITING_PAYMENT, Set.of(TenderWorkflowStatus.COMPLETED));
    }

    private final TenderWorkflowRepository workflowRepository;
    private final TenderWorkflowStatusHistoryRepository historyRepository;
    private final TenderWorkflowCommentRepository commentRepository;
    private final TenderWorkflowFileRepository fileRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final LegalCounterpartyRepository counterpartyRepository;
    private final LegalCounterpartyTenderLinkRepository tenderLinkRepository;
    private final LegalCounterpartyService legalCounterpartyService;

    public TenderWorkflowService(
            TenderWorkflowRepository workflowRepository,
            TenderWorkflowStatusHistoryRepository historyRepository,
            TenderWorkflowCommentRepository commentRepository,
            TenderWorkflowFileRepository fileRepository,
            TenderRepository tenderRepository,
            UserRepository userRepository,
            LegalCounterpartyRepository counterpartyRepository,
            LegalCounterpartyTenderLinkRepository tenderLinkRepository,
            LegalCounterpartyService legalCounterpartyService
    ) {
        this.workflowRepository = workflowRepository;
        this.historyRepository = historyRepository;
        this.commentRepository = commentRepository;
        this.fileRepository = fileRepository;
        this.tenderRepository = tenderRepository;
        this.userRepository = userRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.tenderLinkRepository = tenderLinkRepository;
        this.legalCounterpartyService = legalCounterpartyService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_VIEW')")
    public TenderWorkflowPageResponse list(
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
        Page<TenderWorkflow> result = workflowRepository.findAll(
                buildSpecification(status, priority, responsibleManagerId, supplyUserId, lawyerUserId,
                        purchaseTenderId, notificationNumber, inn, dateFrom, dateTo),
                PageRequest.of(normalizePage(page), normalizeSize(size), resolveSort(sort, direction))
        );

        TenderWorkflowPageResponse response = new TenderWorkflowPageResponse();
        response.setItems(result.getContent().stream().map(this::toSummaryResponse).toList());
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        return response;
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_CREATE')")
    public TenderWorkflowDetailsResponse create(TenderWorkflowCreateRequest request) {
        if (request == null || request.getPurchaseTenderId() == null) {
            throw new BadRequestException("purchaseTenderId is required");
        }
        if (hasAssignmentFields(request)) {
            requireAuthority("TENDER_WORKFLOW_ASSIGN_USERS");
        }

        Tender tender = tenderRepository.findById(request.getPurchaseTenderId())
                .orElseThrow(() -> new NotFoundException("Purchase tender not found: " + request.getPurchaseTenderId()));
        if (workflowRepository.existsByPurchaseTenderDbId(tender.getDbId())) {
            throw new ConflictException("Workflow already exists for purchase tender: " + tender.getDbId());
        }

        User actor = currentUser();
        TenderWorkflow workflow = new TenderWorkflow();
        workflow.setPurchaseTender(tender);
        workflow.setStatus(TenderWorkflowStatus.NEW);
        workflow.setPriority(parsePriority(request.getPriority() == null ? null : request.getPriority().getValue(), TenderWorkflowPriority.NORMAL));
        workflow.setResponsibleManager(resolveOptionalUser(request.getResponsibleManagerId(), "responsibleManagerId"));
        workflow.setSupplyUser(resolveOptionalUser(request.getSupplyUserId(), "supplyUserId"));
        workflow.setLawyerUser(resolveOptionalUser(request.getLawyerUserId(), "lawyerUserId"));
        workflow.setApprovedBy(resolveOptionalUser(request.getApprovedById(), "approvedById"));
        workflow.setResultComment(trimToNull(request.getResultComment()));
        workflow.setCreatedBy(actor);
        workflow.setUpdatedBy(actor);

        TenderWorkflow saved = workflowRepository.save(workflow);
        saveHistory(saved, null, TenderWorkflowStatus.NEW, actor, "Workflow created");
        log.info("Tender workflow created: workflowId={}, purchaseTenderId={}, actor={}", saved.getId(), tender.getDbId(), actor.getUsername());
        return toDetailsResponse(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_VIEW')")
    public TenderWorkflowDetailsResponse getById(Long id) {
        return toDetailsResponse(requireWorkflow(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_EDIT')")
    public TenderWorkflowDetailsResponse update(Long id, TenderWorkflowUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (hasAssignmentFields(request)) {
            requireAuthority("TENDER_WORKFLOW_ASSIGN_USERS");
        }

        TenderWorkflow workflow = requireWorkflow(id);
        User actor = currentUser();
        if (request.getPriority() != null) {
            workflow.setPriority(parsePriority(request.getPriority().getValue(), workflow.getPriority()));
        }
        if (request.getResponsibleManagerId() != null) {
            workflow.setResponsibleManager(resolveOptionalUser(request.getResponsibleManagerId(), "responsibleManagerId"));
        }
        if (request.getSupplyUserId() != null) {
            workflow.setSupplyUser(resolveOptionalUser(request.getSupplyUserId(), "supplyUserId"));
        }
        if (request.getLawyerUserId() != null) {
            workflow.setLawyerUser(resolveOptionalUser(request.getLawyerUserId(), "lawyerUserId"));
        }
        if (request.getApprovedById() != null) {
            requireAuthority("TENDER_WORKFLOW_APPROVE");
            workflow.setApprovedBy(resolveOptionalUser(request.getApprovedById(), "approvedById"));
        }
        if (request.getRejectionReason() != null) {
            workflow.setRejectionReason(trimToNull(request.getRejectionReason()));
        }
        if (request.getLostReason() != null) {
            workflow.setLostReason(trimToNull(request.getLostReason()));
        }
        if (request.getResultComment() != null) {
            workflow.setResultComment(trimToNull(request.getResultComment()));
        }
        workflow.setUpdatedBy(actor);

        TenderWorkflow saved = workflowRepository.save(workflow);
        log.info("Tender workflow updated: workflowId={}, actor={}", saved.getId(), actor.getUsername());
        return toDetailsResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_CHANGE_STATUS')")
    public TenderWorkflowDetailsResponse changeStatus(Long id, TenderWorkflowStatusChangeRequest request) {
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("status is required");
        }

        TenderWorkflow workflow = requireWorkflow(id);
        TenderWorkflowStatus oldStatus = workflow.getStatus();
        TenderWorkflowStatus newStatus = parseStatus(request.getStatus().getValue());
        validateTransition(oldStatus, newStatus);

        if (newStatus == TenderWorkflowStatus.REJECTED) {
            requireAuthority("TENDER_WORKFLOW_REJECT");
            String rejectionReason = trimToNull(request.getRejectionReason());
            if (rejectionReason == null) {
                throw new BadRequestException("rejectionReason is required for REJECTED status");
            }
            workflow.setRejectionReason(rejectionReason);
        }
        if (newStatus == TenderWorkflowStatus.LOST) {
            String lostReason = trimToNull(request.getLostReason());
            if (lostReason == null) {
                throw new BadRequestException("lostReason is required for LOST status");
            }
            workflow.setLostReason(lostReason);
        }
        if (oldStatus == TenderWorkflowStatus.APPROVAL
                && newStatus == TenderWorkflowStatus.COMMERCIAL_PROPOSAL_PREPARATION) {
            requireAuthority("TENDER_WORKFLOW_APPROVE");
            workflow.setApprovedBy(currentUser());
        }

        User actor = currentUser();
        String commentText = trimToNull(request.getComment());
        workflow.setStatus(newStatus);
        workflow.setUpdatedBy(actor);
        TenderWorkflow saved = workflowRepository.save(workflow);

        saveHistory(saved, oldStatus, newStatus, actor, commentText);
        if (commentText != null) {
            saveComment(saved, actor, TenderWorkflowCommentType.GENERAL, commentText);
        }
        log.info("Tender workflow status changed: workflowId={}, oldStatus={}, newStatus={}, actor={}",
                saved.getId(), oldStatus, newStatus, actor.getUsername());
        return toDetailsResponse(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_VIEW')")
    public List<TenderWorkflowStatusHistoryResponse> listHistory(Long workflowId) {
        requireWorkflow(workflowId);
        return historyRepository.findAllByWorkflowIdOrderByCreatedAtDescIdDesc(workflowId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_COMMENT')")
    public List<TenderWorkflowCommentResponse> listComments(Long workflowId) {
        requireWorkflow(workflowId);
        return commentRepository.findAllByWorkflowIdOrderByCreatedAtDescIdDesc(workflowId).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_COMMENT')")
    public TenderWorkflowCommentResponse addComment(Long workflowId, TenderWorkflowCommentCreateRequest request) {
        if (request == null || trimToNull(request.getText()) == null) {
            throw new BadRequestException("text is required");
        }
        TenderWorkflow workflow = requireWorkflow(workflowId);
        User actor = currentUser();
        TenderWorkflowComment saved = saveComment(
                workflow,
                actor,
                parseCommentType(request.getCommentType() == null ? null : request.getCommentType().getValue()),
                request.getText()
        );
        log.info("Tender workflow comment added: workflowId={}, commentId={}, actor={}", workflowId, saved.getId(), actor.getUsername());
        return toCommentResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_COMMENT')")
    public TenderWorkflowCommentResponse updateComment(Long workflowId, Long commentId, TenderWorkflowCommentUpdateRequest request) {
        if (request == null || trimToNull(request.getText()) == null) {
            throw new BadRequestException("text is required");
        }
        TenderWorkflowComment comment = requireComment(workflowId, commentId);
        comment.setText(request.getText().trim());
        if (request.getCommentType() != null) {
            comment.setCommentType(parseCommentType(request.getCommentType().getValue()));
        }
        TenderWorkflowComment saved = commentRepository.save(comment);
        log.info("Tender workflow comment updated: workflowId={}, commentId={}, actor={}", workflowId, commentId, currentUsername());
        return toCommentResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_COMMENT')")
    public String deleteComment(Long workflowId, Long commentId) {
        TenderWorkflowComment comment = requireComment(workflowId, commentId);
        commentRepository.delete(comment);
        log.info("Tender workflow comment deleted: workflowId={}, commentId={}, actor={}", workflowId, commentId, currentUsername());
        return "Комментарий удален";
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_UPLOAD_FILE')")
    public List<TenderWorkflowFileResponse> listFiles(Long workflowId) {
        requireWorkflow(workflowId);
        return fileRepository.findAllByWorkflowIdOrderByCreatedAtDescIdDesc(workflowId).stream()
                .map(this::toFileResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_UPLOAD_FILE')")
    public TenderWorkflowFileResponse addFile(Long workflowId, TenderWorkflowFileCreateRequest request) {
        if (request == null || trimToNull(request.getFileName()) == null || trimToNull(request.getStoragePath()) == null) {
            throw new BadRequestException("fileName and storagePath are required");
        }
        TenderWorkflow workflow = requireWorkflow(workflowId);
        User actor = currentUser();
        TenderWorkflowFile file = new TenderWorkflowFile();
        file.setWorkflow(workflow);
        file.setUploadedBy(actor);
        file.setFileName(request.getFileName().trim());
        file.setFileType(trimToNull(request.getFileType()));
        file.setStoragePath(request.getStoragePath().trim());

        TenderWorkflowFile saved = fileRepository.save(file);
        log.info("Tender workflow file added: workflowId={}, fileId={}, actor={}", workflowId, saved.getId(), actor.getUsername());
        return toFileResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_UPLOAD_FILE')")
    public String deleteFile(Long workflowId, Long fileId) {
        TenderWorkflowFile file = fileRepository.findByIdAndWorkflowId(fileId, workflowId)
                .orElseThrow(() -> new NotFoundException("File not found: " + fileId));
        fileRepository.delete(file);
        log.info("Tender workflow file deleted: workflowId={}, fileId={}, actor={}", workflowId, fileId, currentUsername());
        return "Файл удален";
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_VIEW')")
    public List<LegalCounterpartySummaryResponse> listCounterparties(Long workflowId) {
        TenderWorkflow workflow = requireWorkflow(workflowId);
        return tenderLinkRepository.findAllByTenderId(workflow.getPurchaseTender().getId()).stream()
                .map(LegalCounterpartyTenderLink::getCounterparty)
                .map(this::toLegalCounterpartySummary)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_EDIT')")
    public LegalCounterpartyDetailsResponse linkCounterparty(Long workflowId, TenderWorkflowCounterpartyLinkRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        TenderWorkflow workflow = requireWorkflow(workflowId);
        LegalCounterparty counterparty = resolveCounterpartyForLink(request);
        String tenderId = workflow.getPurchaseTender().getId();

        tenderLinkRepository.findByTenderIdAndCounterpartyId(tenderId, counterparty.getId())
                .orElseGet(() -> {
                    LegalCounterpartyTenderLink link = new LegalCounterpartyTenderLink();
                    link.setCounterparty(counterparty);
                    link.setTenderId(tenderId);
                    link.setTenderName(workflow.getPurchaseTender().getTitle());
                    link.setTenderUrl(resolveTenderUrl(workflow.getPurchaseTender()));
                    link.setRelationType(trimToNull(request.getRelationType()) == null ? "CUSTOMER" : request.getRelationType().trim());
                    return tenderLinkRepository.save(link);
                });

        workflow.setUpdatedBy(currentUser());
        workflowRepository.save(workflow);
        log.info("Tender workflow counterparty linked: workflowId={}, counterpartyId={}, actor={}",
                workflowId, counterparty.getId(), currentUsername());
        return legalCounterpartyService.getById(counterparty.getId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('TENDER_WORKFLOW_EDIT')")
    public LegalCounterpartyCheckResponse createCounterpartyCheck(Long workflowId, TenderWorkflowCounterpartyCheckRequest request) {
        if (request == null || request.getCheck() == null) {
            throw new BadRequestException("check is required");
        }
        TenderWorkflow workflow = requireWorkflow(workflowId);
        LegalCounterparty counterparty = resolveLinkedCounterparty(workflow, request.getCounterpartyId());
        LegalCounterpartyCheckResponse response = legalCounterpartyService.addCheck(counterparty.getId(), request.getCheck());
        log.info("Tender workflow counterparty check created: workflowId={}, counterpartyId={}, actor={}",
                workflowId, counterparty.getId(), currentUsername());
        return response;
    }

    private Specification<TenderWorkflow> buildSpecification(
            String status,
            String priority,
            Long responsibleManagerId,
            Long supplyUserId,
            Long lawyerUserId,
            Long purchaseTenderId,
            String notificationNumber,
            String inn,
            OffsetDateTime dateFrom,
            OffsetDateTime dateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<TenderWorkflow, Tender> tender = root.join("purchaseTender", JoinType.INNER);

            if (trimToNull(status) != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), parseStatus(status)));
            }
            if (trimToNull(priority) != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), parsePriority(priority, TenderWorkflowPriority.NORMAL)));
            }
            if (responsibleManagerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("responsibleManager").get("id"), responsibleManagerId));
            }
            if (supplyUserId != null) {
                predicates.add(criteriaBuilder.equal(root.get("supplyUser").get("id"), supplyUserId));
            }
            if (lawyerUserId != null) {
                predicates.add(criteriaBuilder.equal(root.get("lawyerUser").get("id"), lawyerUserId));
            }
            if (purchaseTenderId != null) {
                predicates.add(criteriaBuilder.equal(tender.get("dbId"), purchaseTenderId));
            }
            if (trimToNull(notificationNumber) != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(tender.get("notificationNumber")),
                        "%" + notificationNumber.trim().toLowerCase(Locale.ROOT) + "%"
                ));
            }
            if (dateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), toLocalDateTime(dateFrom)));
            }
            if (dateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), toLocalDateTime(dateTo)));
            }
            if (trimToNull(inn) != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<LegalCounterpartyTenderLink> link = subquery.from(LegalCounterpartyTenderLink.class);
                subquery.select(link.get("id"));
                subquery.where(
                        criteriaBuilder.equal(link.get("tenderId"), tender.get("id")),
                        criteriaBuilder.equal(link.get("counterparty").get("inn"), inn.trim())
                );
                predicates.add(criteriaBuilder.exists(subquery));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort resolveSort(String sort, String direction) {
        Sort.Direction resolvedDirection = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String resolvedSort = trimToNull(sort) == null ? "updatedAt" : sort.trim();
        return switch (resolvedSort) {
            case "id", "status", "priority", "createdAt", "updatedAt" -> Sort.by(resolvedDirection, resolvedSort);
            case "purchaseTenderId" -> Sort.by(resolvedDirection, "purchaseTender.dbId");
            case "notificationNumber" -> Sort.by(resolvedDirection, "purchaseTender.notificationNumber");
            case "applicationDeadline" -> Sort.by(resolvedDirection, "purchaseTender.applicationDeadline");
            default -> Sort.by(resolvedDirection, "updatedAt");
        };
    }

    private void validateTransition(TenderWorkflowStatus oldStatus, TenderWorkflowStatus newStatus) {
        if (oldStatus == newStatus) {
            throw new BadRequestException("Workflow already has status: " + newStatus);
        }
        if (isFinalStatus(oldStatus)) {
            throw new BadRequestException("Transition from final status is forbidden: " + oldStatus);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(oldStatus, Set.of()).contains(newStatus)) {
            throw new BadRequestException("Transition is not allowed: " + oldStatus + " -> " + newStatus);
        }
    }

    private boolean isFinalStatus(TenderWorkflowStatus status) {
        return status == TenderWorkflowStatus.COMPLETED
                || status == TenderWorkflowStatus.LOST
                || status == TenderWorkflowStatus.REJECTED;
    }

    private TenderWorkflow requireWorkflow(Long id) {
        if (id == null) {
            throw new BadRequestException("workflow id is required");
        }
        return workflowRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + id));
    }

    private TenderWorkflowComment requireComment(Long workflowId, Long commentId) {
        requireWorkflow(workflowId);
        return commentRepository.findByIdAndWorkflowId(commentId, workflowId)
                .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
    }

    private LegalCounterparty resolveCounterpartyForLink(TenderWorkflowCounterpartyLinkRequest request) {
        if (request.getCounterpartyId() != null) {
            return counterpartyRepository.findById(request.getCounterpartyId())
                    .orElseThrow(() -> new NotFoundException("Counterparty not found: " + request.getCounterpartyId()));
        }
        if (request.getCreateCounterparty() == null) {
            throw new BadRequestException("counterpartyId or createCounterparty is required");
        }
        LegalCounterpartyDetailsResponse created = legalCounterpartyService.create(request.getCreateCounterparty());
        return counterpartyRepository.findById(created.getId())
                .orElseThrow(() -> new NotFoundException("Counterparty not found after creation: " + created.getId()));
    }

    private LegalCounterparty resolveLinkedCounterparty(TenderWorkflow workflow, Long requestedCounterpartyId) {
        String tenderId = workflow.getPurchaseTender().getId();
        if (requestedCounterpartyId != null) {
            return tenderLinkRepository.findByTenderIdAndCounterpartyId(tenderId, requestedCounterpartyId)
                    .map(LegalCounterpartyTenderLink::getCounterparty)
                    .orElseThrow(() -> new NotFoundException("Counterparty is not linked to workflow tender: " + requestedCounterpartyId));
        }
        return tenderLinkRepository.findAllByTenderId(tenderId).stream()
                .findFirst()
                .map(LegalCounterpartyTenderLink::getCounterparty)
                .orElseThrow(() -> new NotFoundException("No counterparty linked to workflow"));
    }

    private TenderWorkflowStatusHistory saveHistory(
            TenderWorkflow workflow,
            TenderWorkflowStatus oldStatus,
            TenderWorkflowStatus newStatus,
            User actor,
            String comment
    ) {
        TenderWorkflowStatusHistory history = new TenderWorkflowStatusHistory();
        history.setWorkflow(workflow);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(actor);
        history.setComment(trimToNull(comment));
        return historyRepository.save(history);
    }

    private TenderWorkflowComment saveComment(
            TenderWorkflow workflow,
            User author,
            TenderWorkflowCommentType commentType,
            String text
    ) {
        TenderWorkflowComment comment = new TenderWorkflowComment();
        comment.setWorkflow(workflow);
        comment.setAuthor(author);
        comment.setCommentType(commentType == null ? TenderWorkflowCommentType.GENERAL : commentType);
        comment.setText(text.trim());
        return commentRepository.save(comment);
    }

    private User currentUser() {
        String username = currentUsername();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return user;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Unauthorized");
        }
        return authentication.getName();
    }

    private void requireAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean granted = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
        if (!granted) {
            throw new AccessDeniedException("Forbidden");
        }
    }

    private boolean hasAssignmentFields(TenderWorkflowCreateRequest request) {
        return request.getResponsibleManagerId() != null
                || request.getSupplyUserId() != null
                || request.getLawyerUserId() != null
                || request.getApprovedById() != null;
    }

    private boolean hasAssignmentFields(TenderWorkflowUpdateRequest request) {
        return request.getResponsibleManagerId() != null
                || request.getSupplyUserId() != null
                || request.getLawyerUserId() != null
                || request.getApprovedById() != null;
    }

    private User resolveOptionalUser(Long userId, String fieldName) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(fieldName + " user not found: " + userId));
    }

    private TenderWorkflowStatus parseStatus(String value) {
        try {
            return TenderWorkflowStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BadRequestException("Unknown workflow status: " + value);
        }
    }

    private TenderWorkflowPriority parsePriority(String value, TenderWorkflowPriority defaultValue) {
        if (trimToNull(value) == null) {
            return defaultValue;
        }
        try {
            return TenderWorkflowPriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BadRequestException("Unknown workflow priority: " + value);
        }
    }

    private TenderWorkflowCommentType parseCommentType(String value) {
        if (trimToNull(value) == null) {
            return TenderWorkflowCommentType.GENERAL;
        }
        try {
            return TenderWorkflowCommentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BadRequestException("Unknown workflow comment type: " + value);
        }
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and 100");
        }
        return size;
    }

    private TenderWorkflowSummaryResponse toSummaryResponse(TenderWorkflow workflow) {
        Tender tender = workflow.getPurchaseTender();
        TenderWorkflowSummaryResponse response = new TenderWorkflowSummaryResponse();
        response.setId(workflow.getId());
        response.setPurchaseTenderId(tender.getDbId());
        response.setPurchaseId(tender.getId());
        response.setNotificationNumber(tender.getNotificationNumber());
        response.setTitle(tender.getTitle());
        response.setStatus(workflow.getStatus().name());
        response.setPriority(workflow.getPriority().name());
        response.setResponsibleManagerId(idOf(workflow.getResponsibleManager()));
        response.setSupplyUserId(idOf(workflow.getSupplyUser()));
        response.setLawyerUserId(idOf(workflow.getLawyerUser()));
        response.setApprovedById(idOf(workflow.getApprovedBy()));
        response.setRejectionReason(workflow.getRejectionReason());
        response.setLostReason(workflow.getLostReason());
        response.setResultComment(workflow.getResultComment());
        response.setCreatedById(idOf(workflow.getCreatedBy()));
        response.setUpdatedById(idOf(workflow.getUpdatedBy()));
        response.setCreatedAt(toOffsetDateTime(workflow.getCreatedAt()));
        response.setUpdatedAt(toOffsetDateTime(workflow.getUpdatedAt()));
        return response;
    }

    private TenderWorkflowDetailsResponse toDetailsResponse(TenderWorkflow workflow) {
        TenderWorkflowSummaryResponse summary = toSummaryResponse(workflow);
        TenderWorkflowDetailsResponse response = new TenderWorkflowDetailsResponse();
        response.setId(summary.getId());
        response.setPurchaseTenderId(summary.getPurchaseTenderId());
        response.setPurchaseId(summary.getPurchaseId());
        response.setNotificationNumber(summary.getNotificationNumber());
        response.setTitle(summary.getTitle());
        response.setStatus(summary.getStatus());
        response.setPriority(summary.getPriority());
        response.setResponsibleManagerId(summary.getResponsibleManagerId());
        response.setSupplyUserId(summary.getSupplyUserId());
        response.setLawyerUserId(summary.getLawyerUserId());
        response.setApprovedById(summary.getApprovedById());
        response.setRejectionReason(summary.getRejectionReason());
        response.setLostReason(summary.getLostReason());
        response.setResultComment(summary.getResultComment());
        response.setCreatedById(summary.getCreatedById());
        response.setUpdatedById(summary.getUpdatedById());
        response.setCreatedAt(summary.getCreatedAt());
        response.setUpdatedAt(summary.getUpdatedAt());
        response.setPurchaseTender(toTenderSummary(workflow.getPurchaseTender()));
        response.setResponsibleManager(toUserResponse(workflow.getResponsibleManager()));
        response.setSupplyUser(toUserResponse(workflow.getSupplyUser()));
        response.setLawyerUser(toUserResponse(workflow.getLawyerUser()));
        response.setApprovedBy(toUserResponse(workflow.getApprovedBy()));
        response.setCreatedBy(toUserResponse(workflow.getCreatedBy()));
        response.setUpdatedBy(toUserResponse(workflow.getUpdatedBy()));
        return response;
    }

    private TenderWorkflowTenderSummaryResponse toTenderSummary(Tender tender) {
        TenderWorkflowTenderSummaryResponse response = new TenderWorkflowTenderSummaryResponse();
        response.setId(tender.getDbId());
        response.setPurchaseId(tender.getId());
        response.setSourceType(tender.getSourceType() == null ? null : tender.getSourceType().name());
        response.setNotificationNumber(tender.getNotificationNumber());
        response.setTitle(tender.getTitle());
        response.setLink(resolveTenderUrl(tender));
        response.setPublicationDateTime(toOffsetDateTime(tender.getPublicationDateTimeUTC()));
        response.setApplicationDeadline(toOffsetDateTime(tender.getApplicationDeadline()));
        return response;
    }

    private TenderWorkflowUserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        TenderWorkflowUserResponse response = new TenderWorkflowUserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setMiddleName(user.getMiddleName());
        response.setLastName(user.getLastName());
        response.setFullName(fullName(user));
        return response;
    }

    private TenderWorkflowStatusHistoryResponse toHistoryResponse(TenderWorkflowStatusHistory history) {
        TenderWorkflowStatusHistoryResponse response = new TenderWorkflowStatusHistoryResponse();
        response.setId(history.getId());
        response.setWorkflowId(history.getWorkflow().getId());
        response.setOldStatus(history.getOldStatus() == null ? null : history.getOldStatus().name());
        response.setNewStatus(history.getNewStatus().name());
        response.setChangedById(idOf(history.getChangedBy()));
        response.setComment(history.getComment());
        response.setCreatedAt(toOffsetDateTime(history.getCreatedAt()));
        return response;
    }

    private TenderWorkflowCommentResponse toCommentResponse(TenderWorkflowComment comment) {
        TenderWorkflowCommentResponse response = new TenderWorkflowCommentResponse();
        response.setId(comment.getId());
        response.setWorkflowId(comment.getWorkflow().getId());
        response.setAuthorId(idOf(comment.getAuthor()));
        response.setCommentType(comment.getCommentType().name());
        response.setText(comment.getText());
        response.setCreatedAt(toOffsetDateTime(comment.getCreatedAt()));
        response.setUpdatedAt(toOffsetDateTime(comment.getUpdatedAt()));
        return response;
    }

    private TenderWorkflowFileResponse toFileResponse(TenderWorkflowFile file) {
        TenderWorkflowFileResponse response = new TenderWorkflowFileResponse();
        response.setId(file.getId());
        response.setWorkflowId(file.getWorkflow().getId());
        response.setUploadedById(idOf(file.getUploadedBy()));
        response.setFileName(file.getFileName());
        response.setFileType(file.getFileType());
        response.setStoragePath(file.getStoragePath());
        response.setCreatedAt(toOffsetDateTime(file.getCreatedAt()));
        return response;
    }

    private LegalCounterpartySummaryResponse toLegalCounterpartySummary(LegalCounterparty counterparty) {
        LegalCounterpartySummaryResponse response = new LegalCounterpartySummaryResponse();
        response.setId(counterparty.getId());
        response.setCompanyName(counterparty.getCompanyName());
        response.setShortName(counterparty.getShortName());
        response.setInn(counterparty.getInn());
        response.setKpp(counterparty.getKpp());
        response.setOgrn(counterparty.getOgrn());
        response.setRegistryType(counterparty.getRegistryType() == null ? null : counterparty.getRegistryType().name());
        response.setOverallScore(counterparty.getOverallScore());
        response.setRiskLevel(counterparty.getRiskLevel() == null ? null : counterparty.getRiskLevel().name());
        response.setWorkProhibited(counterparty.isWorkProhibited());
        response.setLastCheckedAt(toOffsetDateTime(counterparty.getLastCheckedAt()));
        response.setLegalComment(counterparty.getLegalComment());
        response.setUpdatedAt(toOffsetDateTime(counterparty.getUpdatedAt()));
        return response;
    }

    private Long idOf(User user) {
        return user == null ? null : user.getId();
    }

    private String fullName(User user) {
        String fullName = java.util.stream.Stream.of(user.getLastName(), user.getFirstName(), user.getMiddleName())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(" "));
        return fullName.isBlank() ? user.getUsername() : fullName;
    }

    private String resolveTenderUrl(Tender tender) {
        if (trimToNull(tender.getLink()) != null) {
            return tender.getLink();
        }
        if (trimToNull(tender.getEisLink()) != null) {
            return tender.getEisLink();
        }
        return tender.getEtpLink();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : OffsetDateTime.of(value, ZoneOffset.UTC);
    }

    private OffsetDateTime toOffsetDateTime(Date value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
