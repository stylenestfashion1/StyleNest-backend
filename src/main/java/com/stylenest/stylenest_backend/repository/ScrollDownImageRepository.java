package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.ScrollDownImage;
import com.stylenest.stylenest_backend.enums.Gender;

@Repository
public interface ScrollDownImageRepository extends JpaRepository<ScrollDownImage, Long> {

    List<ScrollDownImage> findByGenderOrderByStepAsc(Gender gender);

    Optional<ScrollDownImage> findByGenderAndStep(Gender gender, Integer step);

}
