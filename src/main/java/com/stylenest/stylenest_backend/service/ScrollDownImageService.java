package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageRequest;
import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageResponse;
import com.stylenest.stylenest_backend.enums.Gender;

public interface ScrollDownImageService {

    List<ScrollDownImageResponse> getByGender(Gender gender);

    ScrollDownImageResponse upsert(Gender gender, int step, ScrollDownImageRequest request);

    void reset(Gender gender, int step);

}
