package com.stylenest.stylenest_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.stylenest.stylenest_backend.entity.DiscountOffer;

public interface DiscountOfferRepository extends JpaRepository<DiscountOffer, Long>, JpaSpecificationExecutor<DiscountOffer> {

    boolean existsByMobileNumber(String mobileNumber);
}
