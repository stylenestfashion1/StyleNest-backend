package com.stylenest.stylenest_backend.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageRequest;
import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageResponse;
import com.stylenest.stylenest_backend.entity.ScrollDownImage;
import com.stylenest.stylenest_backend.enums.Gender;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.mapper.ScrollDownImageMapper;
import com.stylenest.stylenest_backend.repository.ScrollDownImageRepository;
import com.stylenest.stylenest_backend.service.ScrollDownImageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScrollDownImageServiceImpl implements ScrollDownImageService {

    private final ScrollDownImageRepository scrollDownImageRepository;
    private final ScrollDownImageMapper scrollDownImageMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ScrollDownImageResponse> getByGender(Gender gender) {

        return scrollDownImageRepository.findByGenderOrderByStepAsc(gender)
                .stream()
                .map(scrollDownImageMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ScrollDownImageResponse upsert(Gender gender, int step, ScrollDownImageRequest request) {

        validateStep(step);

        ScrollDownImage image = scrollDownImageRepository
                .findByGenderAndStep(gender, step)
                .orElseGet(() -> ScrollDownImage.builder()
                        .gender(gender)
                        .step(step)
                        .build());

        image.setImageUrl(request.getImageUrl());

        image = scrollDownImageRepository.save(image);

        return scrollDownImageMapper.toResponse(image);
    }

    @Override
    @Transactional
    public void reset(Gender gender, int step) {

        validateStep(step);

        // Idempotent: the frontend falls back to its own default image
        // when no row exists, so "reset" just needs the row gone -- not
        // finding one is already the desired end state, not an error.
        Optional<ScrollDownImage> existing = scrollDownImageRepository.findByGenderAndStep(gender, step);
        existing.ifPresent(scrollDownImageRepository::delete);
    }

    private void validateStep(int step) {
        if (step < 1 || step > 3) {
            throw new BadRequestException("step must be 1, 2, or 3.");
        }
    }
}
