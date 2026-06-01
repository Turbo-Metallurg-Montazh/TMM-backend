package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.IlliquidAssets;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IlliquidAssetsRepository extends JpaRepository<IlliquidAssets, Long>, JpaSpecificationExecutor<IlliquidAssets> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select asset from IlliquidAssets asset where asset.id = :id")
    Optional<IlliquidAssets> findByIdForUpdate(@Param("id") Long id);
}
