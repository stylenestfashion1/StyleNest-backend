package com.stylenest.stylenest_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.entity.Invoice;
import com.stylenest.stylenest_backend.entity.Order;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByRetailOrder(Order order);

    Optional<Invoice> findByBulkOrder(BulkOrder bulkOrder);

    boolean existsByRetailOrder(Order order);

    boolean existsByBulkOrder(BulkOrder bulkOrder);
}
