package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.BulkCategory;
import com.stylenest.stylenest_backend.enums.Gender;

public interface BulkCategoryRepository extends JpaRepository<BulkCategory, Long> {

    boolean existsByNameIgnoreCaseAndGender(String name, Gender gender);

    boolean existsByNameIgnoreCase(String name);

    Optional<BulkCategory> findBySlug(String slug);

    List<BulkCategory> findByActiveTrue();

    List<BulkCategory> findByGenderAndActiveTrue(Gender gender);
}
