package com.stylenest.stylenest_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.RentalCatalogItem;

public interface RentalCatalogItemRepository extends JpaRepository<RentalCatalogItem, Long> {
}
