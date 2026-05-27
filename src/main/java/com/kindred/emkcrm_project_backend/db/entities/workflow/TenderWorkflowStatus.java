package com.kindred.emkcrm_project_backend.db.entities.workflow;

public enum TenderWorkflowStatus {
    NEW,
    PROFILE_REVIEW,
    FEASIBILITY_REVIEW,
    CONTRACTOR_CHECK,
    PRICE_CALCULATION,
    APPROVAL,
    COMMERCIAL_PROPOSAL_PREPARATION,
    READY_FOR_BIDDING,
    BIDDING,
    CONTRACT_EXECUTION,
    WAITING_PAYMENT,
    COMPLETED,
    LOST,
    REJECTED
}
