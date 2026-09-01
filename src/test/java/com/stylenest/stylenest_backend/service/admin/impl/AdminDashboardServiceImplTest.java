package com.stylenest.stylenest_backend.service.admin.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stylenest.stylenest_backend.dto.admin.LatestOrderResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.OrderItemRepository;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    private AdminDashboardServiceImpl adminDashboardService;

    @Test
    void getLatestOrders_handlesGuestOrdersWithoutThrowing() {

        adminDashboardService = new AdminDashboardServiceImpl(
                productRepository, categoryRepository, userRepository,
                orderRepository, productVariantRepository, orderItemRepository);

        User user = User.builder().id(1L).fullName("Registered Customer").build();

        Order registeredOrder = Order.builder()
                .id(1L)
                .orderNumber("SN-1")
                .user(user)
                .totalAmount(BigDecimal.TEN)
                .orderStatus(OrderStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();

        // Guest order: no User -- this used to NPE on order.getUser().getFullName().
        Order guestOrder = Order.builder()
                .id(2L)
                .orderNumber("SN-2")
                .user(null)
                .guestEmail("guest@example.com")
                .shippingFullName("Guest Customer")
                .totalAmount(BigDecimal.valueOf(20))
                .orderStatus(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(orderRepository.findTop10ByOrderByCreatedAtDesc())
                .thenReturn(List.of(registeredOrder, guestOrder));

        List<LatestOrderResponse> result = adminDashboardService.getLatestOrders();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCustomerName()).isEqualTo("Registered Customer");
        assertThat(result.get(1).getCustomerName()).isEqualTo("Guest Customer");
    }
}
