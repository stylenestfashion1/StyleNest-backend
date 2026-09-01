package com.stylenest.stylenest_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Shipment;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrder(Order order);

    Optional<Shipment> findByOrder_Id(Long orderId);
}
