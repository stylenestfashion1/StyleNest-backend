package com.stylenest.stylenest_backend.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageRequest;
import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageResponse;
import com.stylenest.stylenest_backend.enums.Gender;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.ScrollDownImageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/scroll-images")
@RequiredArgsConstructor
public class AdminScrollDownImageController {

    private final ScrollDownImageService scrollDownImageService;

    @PutMapping("/{gender}/{step}")
    public ResponseEntity<ApiResponse<ScrollDownImageResponse>> upsert(
            @PathVariable Gender gender,
            @PathVariable int step,
            @Valid @RequestBody ScrollDownImageRequest request) {

        ScrollDownImageResponse response = scrollDownImageService.upsert(gender, step, request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Scroll-down image saved successfully",
                        response));
    }

    @DeleteMapping("/{gender}/{step}")
    public ResponseEntity<ApiResponse<Void>> reset(
            @PathVariable Gender gender,
            @PathVariable int step) {

        scrollDownImageService.reset(gender, step);

        return ResponseEntity.ok(
                ApiResponse.success("Scroll-down image reset successfully"));
    }
}
