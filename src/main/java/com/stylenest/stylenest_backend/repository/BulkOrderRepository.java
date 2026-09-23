package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.BulkOrder;

public interface BulkOrderRepository extends JpaRepository<BulkOrder, Long> {

    Optional<BulkOrder> findByBulkOrderNumber(String bulkOrderNumber);

    List<BulkOrder> findAllByOrderByCreatedAtDesc();
}
