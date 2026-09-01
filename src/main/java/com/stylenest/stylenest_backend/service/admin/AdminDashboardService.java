package com.stylenest.stylenest_backend.service.admin;

import java.util.List;

import com.stylenest.stylenest_backend.dto.admin.DashboardResponse;
import com.stylenest.stylenest_backend.dto.admin.LatestOrderResponse;
import com.stylenest.stylenest_backend.dto.admin.LowStockProductResponse;
import com.stylenest.stylenest_backend.dto.admin.TopSellingProductResponse;

public interface AdminDashboardService {

    DashboardResponse getDashboard();
    List<LatestOrderResponse> getLatestOrders();
    List<LowStockProductResponse> getLowStockProducts();
    List<TopSellingProductResponse> getTopSellingProducts();

}