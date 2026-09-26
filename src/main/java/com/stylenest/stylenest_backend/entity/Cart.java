package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.stylenest.stylenest_backend.enums.Currency;

import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(name = "cart")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(
    	    mappedBy = "cart",
    	    cascade = CascadeType.ALL,
    	    orphanRemoval = true,
    	    fetch = FetchType.LAZY
    	)
    	@Builder.Default
    	private List<CartItem> items = new ArrayList<>();
    
//
    @Builder.Default
    private BigDecimal totalPrice = BigDecimal.ZERO;

    // The single currency this cart's items are priced in -- set on the
    // first addToCart call, null while the cart is empty. A second
    // addToCart in a different currency is rejected server-side (see
    // CartServiceImpl) rather than silently mixing currencies; the cart
    // must be cleared first to switch. Reset to null whenever the cart is
    // fully cleared.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Currency currency;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    
}