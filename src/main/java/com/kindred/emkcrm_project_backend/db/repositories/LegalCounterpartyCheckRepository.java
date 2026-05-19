package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterpartyCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalCounterpartyCheckRepository extends JpaRepository<LegalCounterpartyCheck, Long> {

    List<LegalCounterpartyCheck> findAllByCounterpartyIdOrderByCheckedAtDescIdDesc(Long counterpartyId);
}
