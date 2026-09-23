package com.stylenest.stylenest_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.DiscountSlot;

public interface DiscountSlotRepository extends JpaRepository<DiscountSlot, Long> {

    List<DiscountSlot> findAllByOrderByDiscountPercentageAsc();
}
