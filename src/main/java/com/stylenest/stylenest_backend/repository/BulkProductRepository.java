package com.stylenest.stylenest_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.BulkCategory;
import com.stylenest.stylenest_backend.entity.BulkProduct;

public interface BulkProductRepository extends JpaRepository<BulkProduct, Long> {

    boolean existsByCategory(BulkCategory category);

    List<BulkProduct> findByActiveTrue();

    List<BulkProduct> findByActiveTrueAndCategoryId(Long categoryId);
}
