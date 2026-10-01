package com.stylenest.stylenest_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.RentalCatalog;
import com.stylenest.stylenest_backend.enums.RentalCatalogStatus;

public interface RentalCatalogRepository extends JpaRepository<RentalCatalog, Long> {

    Optional<RentalCatalog> findByShareToken(String shareToken);

    boolean existsByShareToken(String shareToken);

    // Backs the new main-nav "Rentals" entry point (see
    // RentalCatalogServiceImpl.getActiveCatalog) -- if an admin ever leaves
    // more than one catalog ACTIVE at once, the most recently created one
    // wins, deterministically.
    Optional<RentalCatalog> findFirstByStatusOrderByCreatedAtDesc(RentalCatalogStatus status);
}
