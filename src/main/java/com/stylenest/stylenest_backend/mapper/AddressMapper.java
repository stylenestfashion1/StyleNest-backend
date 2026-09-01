package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.address.AddressRequest;
import com.stylenest.stylenest_backend.dto.address.AddressResponse;
import com.stylenest.stylenest_backend.entity.Address;

@Component
public class AddressMapper {

    public Address toEntity(AddressRequest request) {

        return Address.builder()
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .phoneCountryCode(request.getPhoneCountryCode())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .countryCode(request.getCountryCode())
                .postalCode(request.getPostalCode())
                .addressType(request.getAddressType())
                .isDefault(request.getIsDefault())
                .build();
    }

    public AddressResponse toResponse(Address address) {

        return AddressResponse.builder()
                .id(address.getId())
                .fullName(address.getFullName())
                .phone(address.getPhone())
                .phoneCountryCode(address.getPhoneCountryCode())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .country(address.getCountry())
                .countryCode(address.getCountryCode())
                .postalCode(address.getPostalCode())
                .addressType(address.getAddressType())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }

    public void updateEntity(Address address, AddressRequest request) {

        address.setFullName(request.getFullName());
        address.setPhone(request.getPhone());
        address.setPhoneCountryCode(request.getPhoneCountryCode());
        address.setAddressLine1(request.getAddressLine1());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setCountryCode(request.getCountryCode());
        address.setPostalCode(request.getPostalCode());
        address.setAddressType(request.getAddressType());
        address.setIsDefault(request.getIsDefault());
    }
}
