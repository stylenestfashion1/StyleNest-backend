package com.stylenest.stylenest_backend.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.stylenest.stylenest_backend.dto.product.filter.ProductFilterRequest;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

public class ProductSpecification {

    public static Specification<Product> filterProducts(
            ProductFilterRequest request) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // Keyword Search
            if (request.getKeyword() != null &&
                    !request.getKeyword().isBlank()) {

                predicates.add(

                        cb.like(

                                cb.lower(root.get("name")),

                                "%" +
                                request.getKeyword().toLowerCase() +
                                "%"

                        )

                );
                
            }
             // Category Filter
                if (request.getCategoryId() != null) {

                    predicates.add(

                            cb.equal(

                                    root.get("category").get("id"),

                                    request.getCategoryId()

                            )

                    );

                }

             // Gender Filter (segment lives on the product's category)
                if (request.getGender() != null) {

                    predicates.add(

                            cb.equal(

                                    root.get("category").get("gender"),

                                    request.getGender()

                            )

                    );

                }

             // Minimum Price
                if (request.getMinPrice() != null) {

                    predicates.add(

                            cb.greaterThanOrEqualTo(

                                    root.get("price"),

                                    request.getMinPrice()

                            )

                    );

                }

                // Maximum Price
                if (request.getMaxPrice() != null) {

                    predicates.add(

                            cb.lessThanOrEqualTo(

                                    root.get("price"),

                                    request.getMaxPrice()

                            )

                    );

                }

             // Featured Products
                if (request.getFeatured() != null) {

                    predicates.add(

                            cb.equal(

                                    root.get("featured"),

                                    request.getFeatured()

                            )

                    );

                }

             // Trending Products
                if (request.getTrending() != null) {

                    predicates.add(

                            cb.equal(

                                    root.get("trending"),

                                    request.getTrending()

                            )

                    );

                }

             // Active Products
                if (request.getActive() != null) {

                    predicates.add(

                            cb.equal(

                                    root.get("active"),

                                    request.getActive()

                            )

                    );

                }

                
             // Color + Size Filter
                //
                // When BOTH are provided they must match on the SAME variant
                // row (one join, one AND'd predicate) -- not two independent
                // EXISTS-style joins, which would wrongly match a product
                // that has the color on one variant and the size on a
                // different variant.
                boolean hasColor = request.getColor() != null && !request.getColor().isBlank();
                boolean hasSize = request.getSize() != null && !request.getSize().isBlank();

                if (hasColor || hasSize) {

                    Join<Product, ProductVariant> variant =
                            root.join("variants");

                    if (hasColor) {

                        // Color is free-form (not a fixed enum), so any
                        // value is a legal filter -- just normalize case
                        // the same way it's normalized on write (see
                        // ColorNormalizer), no parse-failure branch needed.
                        predicates.add(
                                cb.equal(
                                        variant.get("color"),
                                        com.stylenest.stylenest_backend.util.ColorNormalizer.normalize(request.getColor())
                                )
                        );
                    }

                    if (hasSize) {

                        try {
                            predicates.add(

                                    cb.equal(

                                            variant.get("size"),

                                            Enum.valueOf(
                                                    com.stylenest.stylenest_backend.enums.Size.class,
                                                    request.getSize().toUpperCase()
                                            )

                                    )

                            );
                        } catch (IllegalArgumentException ex) {
                            // Same rationale as the color branch above.
                            predicates.add(cb.disjunction());
                        }
                    }
                }

                query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
            
            

        };
        
        
        

    }
    
    

}