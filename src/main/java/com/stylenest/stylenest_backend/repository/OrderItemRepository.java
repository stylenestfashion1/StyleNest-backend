package com.stylenest.stylenest_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.OrderItem;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	boolean existsByProductVariant_Product_Id(Long productId);

	@Query("""
			SELECT
			oi.productVariant.product.id,
			oi.productVariant.product.name,
			SUM(oi.quantity)
			FROM OrderItem oi
			GROUP BY
			oi.productVariant.product.id,
			oi.productVariant.product.name
			ORDER BY SUM(oi.quantity) DESC
			""")
			List<Object[]> getTopSellingProducts();
}