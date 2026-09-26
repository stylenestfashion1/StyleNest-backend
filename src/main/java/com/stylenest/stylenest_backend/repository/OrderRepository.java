package com.stylenest.stylenest_backend.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
	List<Order> findTop10ByOrderByCreatedAtDesc();
    List<Order> findByUser(User user);
    List<Order> findAllByOrderByCreatedAtDesc();
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Order> findByUserOrderByCreatedAtDesc(User user);
    boolean existsByAddress_Id(Long addressId);
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByUserAndOrderStatusAndPaymentMethodNot(User user, OrderStatus orderStatus, PaymentMethod paymentMethod);
    List<Order> findByGuestEmailAndOrderStatusAndPaymentMethodNot(String guestEmail, OrderStatus orderStatus, PaymentMethod paymentMethod);

    // Historical orders predate the currency column -- see
    // PricingBackfillRunner, which backfills every one of these to INR
    // (verified: every order ever placed shipped to India).
    List<Order> findByCurrencyIsNull();

      @Query("""
    		SELECT COALESCE(SUM(o.totalAmount), 0)
    		FROM Order o
    		WHERE o.orderStatus <> com.stylenest.stylenest_backend.enums.OrderStatus.CANCELLED
    		""")
    		BigDecimal getTotalRevenue();

}