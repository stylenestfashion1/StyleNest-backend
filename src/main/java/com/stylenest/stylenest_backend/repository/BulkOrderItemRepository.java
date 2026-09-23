package com.stylenest.stylenest_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.BulkOrderItem;
import com.stylenest.stylenest_backend.entity.BulkProduct;

public interface BulkOrderItemRepository extends JpaRepository<BulkOrderItem, Long> {

    boolean existsByBulkProduct(BulkProduct bulkProduct);
}
