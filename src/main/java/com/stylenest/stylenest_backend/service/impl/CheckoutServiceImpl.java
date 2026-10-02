package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.address.AddressResponse;
import com.stylenest.stylenest_backend.dto.checkout.CheckoutItemResponse;
import com.stylenest.stylenest_backend.dto.checkout.CheckoutResponse;
import com.stylenest.stylenest_backend.entity.Address;
import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.CartItem;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.exception.InsufficientStockException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.AddressMapper;
import com.stylenest.stylenest_backend.repository.AddressRepository;
import com.stylenest.stylenest_backend.repository.CartRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.service.CheckoutService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CheckoutServiceImpl implements CheckoutService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final com.stylenest.stylenest_backend.service.shipping.ShippingCalculationService shippingCalculationService;

    private User getCurrentUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutResponse getCheckout() {

        User user = getCurrentUser();

        Optional<Cart> cart = cartRepository.findByUser(user);

        if (cart.isEmpty() || cart.get().getItems().isEmpty()) {
            return emptyCheckout(user);
        }

        for (CartItem item : cart.get().getItems()) {

            if (item.getQuantity() > item.getProductVariant().getStock()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product: "
                                + item.getProductVariant().getProduct().getName());
            }
        }

        Address defaultAddress = addressRepository
                .findByUserAndIsDefaultTrue(user)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Default address not found."));

        List<CheckoutItemResponse> items = cart.get().getItems()
                .stream()
                .map(this::mapItem)
                .toList();

        AddressResponse addressResponse =
                addressMapper.toResponse(defaultAddress);

        BigDecimal subtotal = cart.get().getTotalPrice();
        BigDecimal shippingFee = shippingCalculationService.calculateForCart(cart.get(), defaultAddress);
        BigDecimal grandTotal = subtotal.add(shippingFee != null ? shippingFee : BigDecimal.ZERO);

        return CheckoutResponse.builder()
                .items(items)
                .shippingAddress(addressResponse)
                .currency(cart.get().getCurrency())
                .subtotalAmount(subtotal)
                .shippingFee(shippingFee)
                .totalAmount(grandTotal)
                .build();
    }

    private CheckoutResponse emptyCheckout(User user) {

        AddressResponse addressResponse = addressRepository
                .findByUserAndIsDefaultTrue(user)
                .map(addressMapper::toResponse)
                .orElse(null);

        return CheckoutResponse.builder()
                .items(List.of())
                .shippingAddress(addressResponse)
                .subtotalAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private CheckoutItemResponse mapItem(CartItem item) {

        BigDecimal subtotal = item.getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        return CheckoutItemResponse.builder()
                .variantId(item.getProductVariant().getId())
                .productName(item.getProductVariant().getProduct().getName())
                .color(item.getProductVariant().getColor())
                .size(item.getProductVariant().getSize().name())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(subtotal)
                .build();
    }
}