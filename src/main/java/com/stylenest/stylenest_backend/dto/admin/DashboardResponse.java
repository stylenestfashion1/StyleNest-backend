package com.stylenest.stylenest_backend.dto.admin;

import java.math.BigDecimal;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private Long totalProducts;

    private Long totalCategories;

    private Long totalCustomers;

    private Long totalOrders;

    private BigDecimal totalRevenue;

}