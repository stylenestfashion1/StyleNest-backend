package com.stylenest.stylenest_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.address.AddressRequest;
import com.stylenest.stylenest_backend.dto.address.AddressResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.AddressService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @Valid @RequestBody AddressRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Address added successfully",
                        addressService.addAddress(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Addresses fetched successfully",
                        addressService.getAddresses()));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddressById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Address fetched successfully",
                        addressService.getAddressById(id)
                ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Address updated successfully",
                        addressService.updateAddress(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable Long id) {

        addressService.deleteAddress(id);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Address deleted successfully",
                        null));
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<ApiResponse<Void>> setDefaultAddress(
            @PathVariable Long id) {

        addressService.setDefaultAddress(id);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Default address updated successfully",
                        null));
    }
}