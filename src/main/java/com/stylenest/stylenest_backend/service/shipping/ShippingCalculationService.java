package com.stylenest.stylenest_backend.service.shipping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.order.GuestOrderItemRequest;
import com.stylenest.stylenest_backend.dto.order.GuestShippingAddressRequest;
import com.stylenest.stylenest_backend.dto.shipping.ShippingCalculationRequest;
import com.stylenest.stylenest_backend.dto.shipping.ShippingCalculationResponse;
import com.stylenest.stylenest_backend.entity.Address;
import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.CartItem;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.repository.CartRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingCalculationService {

    private final DtdcRateCalculatorService rateCalculatorService;
    private final ProductVariantRepository productVariantRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ShippingCalculationResponse calculateForRequest(ShippingCalculationRequest request, String currentUserEmail) {
        List<DtdcRateCalculatorService.PhysicalItemSpec> itemSpecs = new ArrayList<>();

        // If items are explicitly provided in request (typical for guest checkout)
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (ShippingCalculationRequest.ShippingItemRequest item : request.getItems()) {
                if (item.getVariantId() != null) {
                    Optional<ProductVariant> variantOpt = productVariantRepository.findById(item.getVariantId());
                    if (variantOpt.isPresent()) {
                        itemSpecs.add(DtdcRateCalculatorService.PhysicalItemSpec.fromVariant(
                                variantOpt.get(),
                                item.getQuantity() != null ? item.getQuantity() : 1
                        ));
                        continue;
                    }
                }
                // Fallback to default apparel spec
                itemSpecs.add(DtdcRateCalculatorService.PhysicalItemSpec.defaultApparel(
                        item.getQuantity() != null ? item.getQuantity() : 1
                ));
            }
        } else if (currentUserEmail != null && !currentUserEmail.isBlank() && !"anonymousUser".equals(currentUserEmail)) {
            // Read authenticated user's cart
            Optional<User> userOpt = userRepository.findByEmail(currentUserEmail);
            if (userOpt.isPresent()) {
                Optional<Cart> cartOpt = cartRepository.findByUser(userOpt.get());
                if (cartOpt.isPresent()) {
                    for (CartItem ci : cartOpt.get().getItems()) {
                        itemSpecs.add(DtdcRateCalculatorService.PhysicalItemSpec.fromVariant(
                                ci.getProductVariant(),
                                ci.getQuantity()
                        ));
                    }
                }
            }
        }

        if (itemSpecs.isEmpty()) {
            itemSpecs.add(DtdcRateCalculatorService.PhysicalItemSpec.defaultApparel(1));
        }

        return rateCalculatorService.calculateShipping(
                request.getPostalCode(),
                request.getCity(),
                request.getState(),
                itemSpecs
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateForCart(Cart cart, Address address) {
        if (address == null || address.getPostalCode() == null || address.getPostalCode().isBlank()) {
            return BigDecimal.ZERO;
        }

        List<DtdcRateCalculatorService.PhysicalItemSpec> itemSpecs = new ArrayList<>();
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                itemSpecs.add(DtdcRateCalculatorService.PhysicalItemSpec.fromVariant(
                        item.getProductVariant(),
                        item.getQuantity()
                ));
            }
        }

        if (itemSpecs.isEmpty()) {
            return BigDecimal.ZERO;
        }

        ShippingCalculationResponse response = rateCalculatorService.calculateShipping(
                address.getPostalCode(),
                address.getCity(),
                address.getState(),
                itemSpecs
        );

        return response.getTotalShippingFee();
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateForGuest(List<GuestOrderItemRequest> items, GuestShippingAddressRequest address) {
        if (address == null || address.getPostalCode() == null || address.getPostalCode().isBlank()) {
            return BigDecimal.ZERO;
        }

        List<DtdcRateCalculatorService.PhysicalItemSpec> itemSpecs = new ArrayList<>();
        if (items != null) {
            for (GuestOrderItemRequest item : items) {
                if (item.getProductVariantId() != null) {
                    Optional<ProductVariant> variantOpt = productVariantRepository.findById(item.getProductVariantId());
                    if (variantOpt.isPresent()) {
                        itemSpecs.add(DtdcRateCalculatorService.PhysicalItemSpec.fromVariant(
                                variantOpt.get(),
                                item.getQuantity() != null ? item.getQuantity() : 1
                        ));
                        continue;
                    }
                }
                itemSpecs.add(DtdcRateCalculatorService.PhysicalItemSpec.defaultApparel(
                        item.getQuantity() != null ? item.getQuantity() : 1
                ));
            }
        }

        if (itemSpecs.isEmpty()) {
            return BigDecimal.ZERO;
        }

        ShippingCalculationResponse response = rateCalculatorService.calculateShipping(
                address.getPostalCode(),
                address.getCity(),
                address.getState(),
                itemSpecs
        );

        return response.getTotalShippingFee();
    }
}
