package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductPrice;
import com.stylenest.stylenest_backend.enums.Currency;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    Optional<ProductPrice> findByProductAndCurrency(Product product, Currency currency);

    List<ProductPrice> findByProductId(Long productId);
}
