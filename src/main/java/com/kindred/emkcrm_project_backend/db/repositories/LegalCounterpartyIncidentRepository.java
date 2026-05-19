package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterpartyIncident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalCounterpartyIncidentRepository extends JpaRepository<LegalCounterpartyIncident, Long> {

    List<LegalCounterpartyIncident> findAllByCounterpartyIdOrderByIncidentDateDescIdDesc(Long counterpartyId);
}
