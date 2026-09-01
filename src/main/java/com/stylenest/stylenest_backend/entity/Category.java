package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.stylenest.stylenest_backend.enums.Gender;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_category_name_gender",
                columnNames = {"name", "gender"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No longer globally unique on its own: the same category name (e.g.
    // "Jeans") legitimately exists once per Gender. Uniqueness is enforced
    // as the (name, gender) pair above instead.
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    // The customer segment this category belongs to. See enums.Gender for
    // why this lives here rather than on Product.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String imageUrl;

    @Builder.Default
    private Boolean active = true;

    // No CascadeType.REMOVE/ALL and no orphanRemoval here: products are
    // independent catalog data (category_id is NOT NULL on Product, so a
    // product can never be silently "orphaned" either -- see
    // CategoryServiceImpl.deleteCategory, which blocks deletion outright
    // while any product still references this category, rather than
    // cascading the delete). PERSIST/MERGE only, so saving a Category
    // still correctly persists/updates products passed in the same
    // object graph.
    @OneToMany(
    	    mappedBy = "category",
    	    cascade = { CascadeType.PERSIST, CascadeType.MERGE },
    	    fetch = FetchType.LAZY
    	)   @Builder.Default
    private List<Product> products = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    
}