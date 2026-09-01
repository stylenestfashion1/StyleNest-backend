package com.stylenest.stylenest_backend.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.User;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

public class OrderSpecification {

    /**
     * Order ID search is mandatory and always works; customer name/email
     * search works transparently for both registered orders (via the
     * joined User) and guest orders (via the guestEmail/shippingFullName
     * snapshot columns) -- one real, paginated query, no in-memory
     * filtering.
     */
    public static Specification<Order> search(String keyword) {

        return (root, query, cb) -> {

            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }

            String like = "%" + keyword.trim().toLowerCase() + "%";

            Join<Order, User> userJoin = root.join("user", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>(List.of(
                    cb.like(cb.lower(root.get("orderNumber")), like),
                    cb.like(cb.lower(cb.coalesce(userJoin.get("email"), "")), like),
                    cb.like(cb.lower(cb.coalesce(userJoin.get("fullName"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("guestEmail"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("shippingFullName"), "")), like)
            ));

            // Exact numeric Order ID match, if the keyword parses as one.
            try {
                predicates.add(cb.equal(root.get("id"), Long.valueOf(keyword.trim())));
            } catch (NumberFormatException ignored) {
                // Not a numeric ID -- the text predicates above still apply.
            }

            query.distinct(true);

            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
