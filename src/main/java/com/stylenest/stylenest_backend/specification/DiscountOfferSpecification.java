package com.stylenest.stylenest_backend.specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.stylenest.stylenest_backend.dto.admin.AdminDiscountOfferSearchRequest;
import com.stylenest.stylenest_backend.entity.DiscountOffer;

import jakarta.persistence.criteria.Predicate;

public class DiscountOfferSpecification {

    public static Specification<DiscountOffer> search(AdminDiscountOfferSearchRequest request) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            String keyword = request.getKeyword();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("customerName")), like),
                        cb.like(root.get("mobileNumber"), "%" + keyword.trim() + "%")
                ));
            }

            if (request.getDiscountPercentage() != null) {
                predicates.add(cb.equal(root.get("discountPercentage"), request.getDiscountPercentage()));
            }

            if (request.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("generatedAt"), request.getFromDate().atStartOfDay()));
            }

            if (request.getToDate() != null) {
                LocalDateTime endOfDay = request.getToDate().atTime(23, 59, 59);
                predicates.add(cb.lessThanOrEqualTo(root.get("generatedAt"), endOfDay));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
