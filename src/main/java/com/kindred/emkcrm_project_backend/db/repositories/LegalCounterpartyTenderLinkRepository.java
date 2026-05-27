package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterpartyTenderLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalCounterpartyTenderLinkRepository extends JpaRepository<LegalCounterpartyTenderLink, Long> {

    List<LegalCounterpartyTenderLink> findAllByTenderId(String tenderId);

    Optional<LegalCounterpartyTenderLink> findByTenderIdAndCounterpartyId(String tenderId, Long counterpartyId);
}
