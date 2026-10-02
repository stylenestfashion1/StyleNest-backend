package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.stylenest.stylenest_backend.dto.address.AddressResponse;
import com.stylenest.stylenest_backend.dto.checkout.CheckoutResponse;
import com.stylenest.stylenest_backend.entity.Address;
import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.CartItem;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.mapper.AddressMapper;
import com.stylenest.stylenest_backend.repository.AddressRepository;
import com.stylenest.stylenest_backend.repository.CartRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private AddressMapper addressMapper;

    @Mock
    private com.stylenest.stylenest_backend.service.shipping.ShippingCalculationService shippingCalculationService;

    private CheckoutServiceImpl checkoutService;

    private User user;

    @BeforeEach
    void setUp() {

        checkoutService = new CheckoutServiceImpl(
                userRepository, cartRepository, addressRepository, addressMapper, shippingCalculationService);

        user = User.builder().id(1L).email("customer@example.com").build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCheckout_whenNoCartExists_returnsEmptyCheckoutInsteadOfThrowing() {

        when(cartRepository.findByUser(user)).thenReturn(Optional.empty());
        when(addressRepository.findByUserAndIsDefaultTrue(user))
                .thenReturn(Optional.empty());

        CheckoutResponse response = checkoutService.getCheckout();

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getShippingAddress()).isNull();
    }

    @Test
    void getCheckout_whenCartHasNoItems_returnsEmptyCheckoutWithDefaultAddress() {

        Cart cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();
        Address defaultAddress = Address.builder().id(5L).isDefault(true).build();
        AddressResponse addressResponse = AddressResponse.builder().id(5L).build();

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserAndIsDefaultTrue(user))
                .thenReturn(Optional.of(defaultAddress));
        when(addressMapper.toResponse(defaultAddress)).thenReturn(addressResponse);

        CheckoutResponse response = checkoutService.getCheckout();

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getShippingAddress()).isEqualTo(addressResponse);
    }

    @Test
    void getCheckout_whenCartHasItems_returnsPopulatedCheckout() {

        ProductVariant variant = ProductVariant.builder()
                .id(2L)
                .color("BLACK")
                .size(Size.M)
                .stock(10)
                .build();

        com.stylenest.stylenest_backend.entity.Product product =
                com.stylenest.stylenest_backend.entity.Product.builder()
                        .id(3L)
                        .name("Test Dress")
                        .build();
        variant.setProduct(product);

        Cart cart = Cart.builder()
                .id(10L)
                .user(user)
                .totalPrice(BigDecimal.valueOf(500))
                .items(new ArrayList<>())
                .build();

        CartItem item = CartItem.builder()
                .cart(cart)
                .productVariant(variant)
                .quantity(2)
                .price(BigDecimal.valueOf(250))
                .build();

        cart.getItems().add(item);

        Address defaultAddress = Address.builder().id(5L).isDefault(true).build();
        AddressResponse addressResponse = AddressResponse.builder().id(5L).build();

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserAndIsDefaultTrue(user))
                .thenReturn(Optional.of(defaultAddress));
        when(addressMapper.toResponse(defaultAddress)).thenReturn(addressResponse);

        CheckoutResponse response = checkoutService.getCheckout();

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(response.getShippingAddress()).isEqualTo(addressResponse);
    }
}