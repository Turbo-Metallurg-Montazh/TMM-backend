package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.TenderFilter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenderFilterRepository extends JpaRepository<TenderFilter, Long> {
    Iterable<TenderFilter> findAllByActiveIs(boolean active);
    Iterable<TenderFilter> findAllByUserId(long userId);
    Optional<TenderFilter> findByName(String name);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}
