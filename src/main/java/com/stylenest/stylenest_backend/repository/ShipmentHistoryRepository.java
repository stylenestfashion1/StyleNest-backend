package com.stylenest.stylenest_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.entity.ShipmentHistory;

@Repository
public interface ShipmentHistoryRepository extends JpaRepository<ShipmentHistory, Long> {

    List<ShipmentHistory> findByShipmentOrderByTimestampAsc(Shipment shipment);
}
