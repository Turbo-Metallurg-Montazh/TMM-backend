package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.legal.LegalCounterparty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface LegalCounterpartyRepository extends JpaRepository<LegalCounterparty, Long>, JpaSpecificationExecutor<LegalCounterparty> {

    Optional<LegalCounterparty> findByInn(String inn);

    boolean existsByInn(String inn);

    boolean existsByInnAndIdNot(String inn, Long id);
}
