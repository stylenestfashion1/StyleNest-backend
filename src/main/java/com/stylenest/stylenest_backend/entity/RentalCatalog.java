package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.stylenest.stylenest_backend.enums.RentalCatalogStatus;

import jakarta.persistence.*;
import lombok.*;

/**
 * A temporary, festival-style rental catalog (e.g. "Navratri Lehenga Rental
 * 2026") shared with customers via a single stable WhatsApp link --
 * completely separate from the normal retail Product/Order/Payment tables.
 * No customer account, order, or payment is ever created against this
 * entity; a customer only browses it and later visits the shop in person.
 *
 * shareToken is generated exactly once, at creation, and never regenerated
 * -- it is the whole point of "one link, always current" (see
 * RentalCatalogServiceImpl). Adding/editing/removing items never touches it.
 */
@Entity
@Table(name = "rental_catalogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Public, unpredictable identifier used in /rental/{shareToken} --
    // never the numeric id, so nothing about catalog count/creation order
    // is exposed. See RentalCatalogServiceImpl.generateUniqueShareToken.
    @Column(nullable = false, unique = true)
    private String shareToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RentalCatalogStatus status;

    @Builder.Default
    @OneToMany(
            mappedBy = "catalog",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<RentalCatalogItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
