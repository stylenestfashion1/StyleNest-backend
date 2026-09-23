package com.stylenest.stylenest_backend.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.admin.AdminDiscountOfferResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountClaimResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountConfigResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountRangeResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountSlotResponse;
import com.stylenest.stylenest_backend.entity.DiscountOffer;
import com.stylenest.stylenest_backend.entity.DiscountRangeConfig;
import com.stylenest.stylenest_backend.entity.DiscountSlot;

@Component
public class DiscountMapper {

    public DiscountSlotResponse toResponse(DiscountSlot slot) {

        return DiscountSlotResponse.builder()
                .id(slot.getId())
                .discountPercentage(slot.getDiscountPercentage())
                .probabilityPercentage(slot.getProbabilityPercentage())
                .build();
    }

    public DiscountConfigResponse toConfigResponse(List<DiscountSlot> slots) {

        int total = slots.stream().mapToInt(DiscountSlot::getProbabilityPercentage).sum();

        return DiscountConfigResponse.builder()
                .slots(slots.stream().map(this::toResponse).toList())
                .totalProbability(total)
                .build();
    }

    public DiscountRangeResponse toRangeResponse(DiscountRangeConfig range) {

        return DiscountRangeResponse.builder()
                .minDiscountPercentage(range.getMinDiscountPercentage())
                .maxDiscountPercentage(range.getMaxDiscountPercentage())
                .build();
    }

    public DiscountClaimResponse toClaimResponse(DiscountOffer offer) {

        return DiscountClaimResponse.builder()
                .customerName(offer.getCustomerName())
                .discountPercentage(offer.getDiscountPercentage())
                .status(offer.getStatus().name())
                .generatedAt(offer.getGeneratedAt())
                .build();
    }

    public AdminDiscountOfferResponse toAdminResponse(DiscountOffer offer) {

        // Full mobile number shown -- the shopkeeper/admin needs it to
        // identify and, if needed, contact the customer at the counter.
        return AdminDiscountOfferResponse.builder()
                .id(offer.getId())
                .customerName(offer.getCustomerName())
                .mobileNumber(offer.getMobileNumber())
                .discountPercentage(offer.getDiscountPercentage())
                .status(offer.getStatus().name())
                .generatedAt(offer.getGeneratedAt())
                .build();
    }
}
