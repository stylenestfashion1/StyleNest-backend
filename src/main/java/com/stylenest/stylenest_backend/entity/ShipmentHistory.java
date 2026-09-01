package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.stylenest.stylenest_backend.enums.ShipmentStatus;

import jakarta.persistence.*;
import lombok.*;

/**
 * One row per shipment status change -- lets the frontend later render a
 * tracking timeline (Order Confirmed / Packed / Shipped / ...).
 */
@Entity
@Table(name = "shipment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    private String description;

    private String location;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}
