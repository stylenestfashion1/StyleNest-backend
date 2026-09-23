package com.stylenest.stylenest_backend.service.admin.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.admin.DashboardResponse;
import com.stylenest.stylenest_backend.dto.admin.LatestOrderResponse;
import com.stylenest.stylenest_backend.dto.admin.LowStockProductResponse;
import com.stylenest.stylenest_backend.dto.admin.TopSellingProductResponse;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.enums.Role;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.OrderItemRepository;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.service.admin.AdminDashboardService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int LOW_STOCK_LIMIT = 5;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    public DashboardResponse getDashboard() {

        return DashboardResponse.builder()
                .totalProducts(productRepository.count())
                .totalCategories(categoryRepository.count())
                .totalCustomers(userRepository.countByRole(Role.CUSTOMER))
                .totalOrders(orderRepository.count())
                .totalRevenue(orderRepository.getTotalRevenue())
                .build();
    }

    @Override
    public List<LowStockProductResponse> getLowStockProducts() {

        return productVariantRepository
                .findByStockLessThanEqualOrderByStockAsc(LOW_STOCK_LIMIT)
                .stream()
                .map(this::mapLowStockProduct)
                .toList();
    }

    @Override
    public List<LatestOrderResponse> getLatestOrders() {

        return orderRepository.findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(order -> LatestOrderResponse.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .customerName(order.getUser() != null
                                ? order.getUser().getFullName()
                                : order.getShippingFullName())
                        .totalAmount(order.getTotalAmount())
                        .orderStatus(order.getOrderStatus().name())
                        .createdAt(order.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public List<TopSellingProductResponse> getTopSellingProducts() {

        return orderItemRepository.getTopSellingProducts()
                .stream()
                .map(row -> TopSellingProductResponse.builder()
                        .productId((Long) row[0])
                        .productName((String) row[1])
                        .totalSold(((Number) row[2]).longValue())
                        .build())
                .toList();
    }

    private LowStockProductResponse mapLowStockProduct(ProductVariant variant) {

        return LowStockProductResponse.builder()
                .productId(variant.getProduct().getId())
                .productName(variant.getProduct().getName())
                .color(variant.getColor())
                .size(variant.getSize().name())
                .stock(variant.getStock())
                .build();
    }
}