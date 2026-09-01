package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.enums.Gender;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndGender(String name, Gender gender);

    List<Category> findByGender(Gender gender);

}