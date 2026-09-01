package com.stylenest.stylenest_backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageResponse;
import com.stylenest.stylenest_backend.enums.Gender;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.ScrollDownImageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/scroll-images")
@RequiredArgsConstructor
public class ScrollDownImageController {

    private final ScrollDownImageService scrollDownImageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScrollDownImageResponse>>> getByGender(
            @RequestParam Gender gender) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Scroll-down images fetched successfully",
                        scrollDownImageService.getByGender(gender)));
    }
}
