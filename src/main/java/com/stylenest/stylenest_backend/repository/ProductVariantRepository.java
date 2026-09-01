package com.stylenest.stylenest_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.enums.Color;
import com.stylenest.stylenest_backend.enums.Size;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProduct(Product product);
    
    List<ProductVariant> findByStockLessThanEqualOrderByStockAsc(Integer stock);
    boolean existsByProductAndColorAndSize(
            Product product,
            Color color,
            Size size
    );

}