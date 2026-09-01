package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.address.AddressRequest;
import com.stylenest.stylenest_backend.dto.address.AddressResponse;

public interface AddressService {

    AddressResponse addAddress(AddressRequest request);

    List<AddressResponse> getAddresses();

    AddressResponse getAddressById(Long id);

    AddressResponse updateAddress(Long id, AddressRequest request);

    void deleteAddress(Long id);

    void setDefaultAddress(Long id);
}