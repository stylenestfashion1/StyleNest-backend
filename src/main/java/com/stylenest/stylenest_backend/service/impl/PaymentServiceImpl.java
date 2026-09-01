package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.payment.EasebuzzInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.EasebuzzInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.GuestPaymentInitiateRequest;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.InvalidPaymentSignatureException;
import com.stylenest.stylenest_backend.exception.PaymentGatewayException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.repository.CartRepository;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.service.EasebuzzClient;
import com.stylenest.stylenest_backend.service.EasebuzzHashService;
import com.stylenest.stylenest_backend.service.OrderService;
import com.stylenest.stylenest_backend.service.PaymentService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    // Straight from the Easebuzz-official validation rules for the
    // Initiate Payment API (paywitheasebuzz-php-lib, utils.php).
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+\\d{1,4}[-]?)?\\d{5,20}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9&'\\-._ ()/,@]{1,150}$");

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final EasebuzzHashService hashService;
    private final EasebuzzClient easebuzzClient;

    @Value("${easebuzz.key}")
    private String merchantKey;

    @Value("${easebuzz.salt}")
    private String salt;

    @Value("${easebuzz.env}")
    private String env;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public EasebuzzInitiateResponse initiate(EasebuzzInitiateRequest request) {

        requireGatewayConfigured();

        PaymentMethod method = request.getPaymentMethod();

        validateMethodSpecificFields(method, request.getUpiVa(), request.getBankCode());

        Order order = orderService.reserveOrderForOnlinePayment(method);

        return completeInitiate(order, method, request.getUpiVa(), request.getBankCode());
    }

    @Override
    public EasebuzzInitiateResponse initiateForGuest(GuestPaymentInitiateRequest request) {

        requireGatewayConfigured();

        PaymentMethod method = request.getPaymentMethod();

        validateMethodSpecificFields(method, request.getUpiVa(), request.getBankCode());

        GuestOrderRequest orderRequest = GuestOrderRequest.builder()
                .guestEmail(request.getGuestEmail())
                .shippingAddress(request.getShippingAddress())
                .paymentMethod(method)
                .items(request.getItems())
                .build();

        Order order = orderService.reserveGuestOrderForOnlinePayment(orderRequest);

        return completeInitiate(order, method, request.getUpiVa(), request.getBankCode());
    }

    private void validateMethodSpecificFields(PaymentMethod method, String upiVa, String bankCode) {

        if (method == null || method == PaymentMethod.COD) {
            throw new BadRequestException(
                    "COD does not use the payment gateway. Place a COD order via POST /api/orders instead.");
        }

        if (method == PaymentMethod.UPI && isBlank(upiVa)) {
            throw new BadRequestException("upiVa is required for UPI payments.");
        }

        if (method == PaymentMethod.NETBANKING && isBlank(bankCode)) {
            throw new BadRequestException("bankCode is required for net banking payments.");
        }
    }

    /**
     * Shared by both the registered and guest initiate flows -- once an
     * Order has been reserved, talking to Easebuzz is identical regardless
     * of who placed it.
     */
    private EasebuzzInitiateResponse completeInitiate(Order order, PaymentMethod method, String upiVa, String bankCode) {

        Map<String, String> initiateParams = buildInitiateParams(order, method);

        String accessKey = easebuzzClient.initiateLink(initiateParams);

        EasebuzzInitiateResponse.EasebuzzInitiateResponseBuilder response = EasebuzzInitiateResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .amount(order.getTotalAmount())
                .paymentMethod(method);

        if (method == PaymentMethod.CARD) {

            String checkoutBase = "prod".equals(env)
                    ? "https://pay.easebuzz.in/pay/"
                    : "https://testpay.easebuzz.in/pay/";

            return response.redirectUrl(checkoutBase + accessKey).build();
        }

        // UPI / NETBANKING -- true Seamless: collected on our own page,
        // sent straight to Easebuzz, never routed through their hosted page.
        Map<String, String> seamlessParams = new HashMap<>();
        seamlessParams.put("access_key", accessKey);

        if (method == PaymentMethod.UPI) {

            seamlessParams.put("payment_mode", "UPI");
            seamlessParams.put("upi_va", upiVa.trim());
            seamlessParams.put("request_mode", "SUVA");

        } else {

            seamlessParams.put("payment_mode", "NB");
            seamlessParams.put("bank_code", bankCode.trim());
        }

        EasebuzzClient.SeamlessResult seamlessResult = easebuzzClient.initiateSeamlessPayment(seamlessParams);

        if (seamlessResult.html()) {
            return response.bankRedirectHtml(seamlessResult.htmlContent()).build();
        }

        Object status = seamlessResult.json() == null ? null : seamlessResult.json().get("status");
        boolean ok = "1".equals(String.valueOf(status)) || (status instanceof Number n && n.intValue() == 1);

        if (!ok) {
            throw new PaymentGatewayException("Could not start the payment. Please try again.");
        }

        String message = method == PaymentMethod.UPI
                ? "Approve the payment request in your UPI app to complete checkout."
                : "Redirecting to your bank for authentication.";

        return response.message(message).build();
    }

    @Override
    public String handleCallback(Map<String, String> responseFields) {

        if (!hashService.verifyResponseHash(responseFields, salt)) {
            throw new InvalidPaymentSignatureException("Payment response could not be verified.");
        }

        String txnid = responseFields.get("txnid");

        Order order = orderRepository.findByOrderNumber(txnid)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for this transaction."));

        // Idempotent: a duplicate callback for an already-settled order is a
        // no-op -- never re-decrement stock, re-clear the cart, or flip a
        // final status a second time.
        if (order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.FAILED) {
            return buildRedirectUrl(order);
        }

        // Same check Easebuzz's own reference implementation performs after
        // hash verification -- the hash-verified status field is the
        // authoritative success/failure signal for this callback.
        boolean success = "success".equalsIgnoreCase(responseFields.get("status"));

        if (success) {

            orderService.markOnlinePaymentPaid(order);

            // Guest orders never had a server-side cart to begin with.
            if (order.getUser() != null) {
                clearCart(order.getUser());
            }

        } else {

            orderService.markOnlinePaymentFailed(order);
        }

        return buildRedirectUrl(order);
    }

    private void clearCart(User user) {

        cartRepository.findByUser(user).ifPresent(cart -> {

            cart.getItems().clear();
            cart.setTotalPrice(BigDecimal.ZERO);

            cartRepository.save(cart);
        });
    }

    private String buildRedirectUrl(Order order) {

        String outcome = order.getPaymentStatus() == PaymentStatus.PAID ? "success" : "failed";

        return frontendUrl + "/orders/" + order.getId() + "?payment=" + outcome;
    }

    private Map<String, String> buildInitiateParams(Order order, PaymentMethod method) {

        String amount = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();

        // Reads the shipping-address SNAPSHOT captured on the order at
        // reservation time -- never the live Address row, which a guest
        // order doesn't even have. Also fixes the same live-dereference
        // issue for registered orders (editing a saved address must never
        // change what gets sent to the payment gateway for an order
        // already in flight).
        String firstname = sanitizeName(order.getShippingFullName());
        String phone = sanitizePhone(order.getShippingPhone());
        String email = order.getUser() != null ? order.getUser().getEmail() : order.getGuestEmail();

        Map<String, String> params = new HashMap<>();
        params.put("key", merchantKey);
        params.put("txnid", order.getOrderNumber());
        params.put("amount", amount);
        params.put("productinfo", "StyleNest Order");
        params.put("firstname", firstname);
        params.put("email", email);
        params.put("phone", phone);
        params.put("surl", appBaseUrl + "/api/payments/easebuzz/callback");
        params.put("furl", appBaseUrl + "/api/payments/easebuzz/callback");
        params.put("udf1", String.valueOf(order.getId()));
        params.put("address1", truncate(order.getShippingAddressLine1(), 100));
        params.put("city", truncate(order.getShippingCity(), 50));
        params.put("state", truncate(order.getShippingState(), 50));
        params.put("country", truncate(order.getShippingCountry(), 50));
        params.put("zipcode", order.getShippingPostalCode());

        if (method != PaymentMethod.CARD) {
            // Only the true-Seamless modes (UPI / Net Banking) request the
            // seamless access_key -- CARD uses the standard hosted redirect.
            params.put("request_flow", "SEAMLESS");
        }

        params.put("hash", hashService.generateInitiateHash(params, salt));

        return params;
    }

    private String sanitizeName(String name) {

        String trimmed = truncate(name, 150);

        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            throw new BadRequestException(
                    "The name on your shipping address contains characters the payment gateway can't accept. "
                            + "Please update your address and try again.");
        }

        return trimmed;
    }

    private String sanitizePhone(String phone) {

        String cleaned = phone == null ? "" : phone.replaceAll("[\\s()]", "");

        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new BadRequestException(
                    "The phone number on your shipping address is not in a valid format. "
                            + "Please update your address and try again.");
        }

        return cleaned;
    }

    private String truncate(String value, int maxLength) {

        if (value == null) {
            return "";
        }

        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void requireGatewayConfigured() {

        if (isBlank(merchantKey) || isBlank(salt)) {
            throw new PaymentGatewayException(
                    "Online payments are not configured yet. Please use Cash on Delivery for now.");
        }
    }
}
