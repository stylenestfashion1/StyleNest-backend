package com.stylenest.stylenest_backend.service.payglocal;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.config.PayGlocalProperties;
import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.entity.QuickPayment;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.InvalidPaymentSignatureException;
import com.stylenest.stylenest_backend.repository.BulkOrderRepository;
import com.stylenest.stylenest_backend.repository.BulkProductRepository;
import com.stylenest.stylenest_backend.repository.QuickPaymentRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.InvoiceGenerationService;
import com.stylenest.stylenest_backend.service.InvoiceService;
import com.stylenest.stylenest_backend.service.impl.QuickPaymentServiceImpl;
import com.stylenest.stylenest_backend.service.payglocal.impl.PayGlocalServiceImpl;

@ExtendWith(MockitoExtension.class)
class PayGlocalVerificationTest {

    @Mock
    private PayGlocalProperties properties;

    @Mock
    private PayGlocalCryptoService cryptoService;

    @Mock
    private BulkOrderRepository bulkOrderRepository;

    @Mock
    private BulkProductRepository bulkProductRepository;

    @Mock
    private QuickPaymentRepository quickPaymentRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private InvoiceGenerationService invoiceGenerationService;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private EmailService emailService;

    @Mock
    private RestClient restClient;

    @InjectMocks
    private PayGlocalServiceImpl payGlocalService;

    @InjectMocks
    private QuickPaymentServiceImpl quickPaymentService;

    private BulkOrder testOrder;

    @BeforeEach
    void setUp() {
        testOrder = new BulkOrder();
        testOrder.setId(1L);
        testOrder.setBulkOrderNumber("BO-TEST-1001");
        testOrder.setTotalAmount(new BigDecimal("5000.00"));
        testOrder.setPaymentStatus(PaymentStatus.PENDING);
        testOrder.setOrderStatus(OrderStatus.PENDING);
    }

    @Test
    void handleWebhook_MissingSignature_ThrowsInvalidPaymentSignatureException() {
        assertThrows(InvalidPaymentSignatureException.class, () -> {
            payGlocalService.handleWebhook("{\"some\":\"payload\"}", null);
        });
        verify(bulkOrderRepository, never()).save(any());
    }

    @Test
    void handleWebhook_InvalidSignature_ThrowsInvalidPaymentSignatureException() {
        String fakeToken = "header.payload.invalid_signature";
        when(cryptoService.verifyJwsToken(fakeToken)).thenReturn(false);

        assertThrows(InvalidPaymentSignatureException.class, () -> {
            payGlocalService.handleWebhook(null, fakeToken);
        });
        verify(bulkOrderRepository, never()).save(any());
    }

    @Test
    void handleWebhook_ValidSignature_MarksOrderPaid() {
        String validToken = "valid.header.signature";
        when(cryptoService.verifyJwsToken(validToken)).thenReturn(true);

        Map<String, Object> claims = new HashMap<>();
        claims.put("merchantTxnId", "BO-TEST-1001");
        claims.put("status", "COMPLETED");
        claims.put("gid", "GID-PAYGLOCAL-12345");
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("totalAmount", "5000.00");
        paymentData.put("txnCurrency", "INR");
        claims.put("paymentData", paymentData);

        when(cryptoService.parseJwsClaims(validToken)).thenReturn(claims);
        when(bulkOrderRepository.findByBulkOrderNumber("BO-TEST-1001")).thenReturn(Optional.of(testOrder));

        payGlocalService.handleWebhook(null, validToken);

        assertEquals(PaymentStatus.PAID, testOrder.getPaymentStatus());
        assertEquals(OrderStatus.CONFIRMED, testOrder.getOrderStatus());
        assertEquals("GID-PAYGLOCAL-12345", testOrder.getPayglocalGid());
        verify(bulkOrderRepository, times(1)).save(testOrder);
    }

    @Test
    void handleWebhook_AmountMismatch_ThrowsBadRequestException() {
        String validToken = "valid.header.signature";
        when(cryptoService.verifyJwsToken(validToken)).thenReturn(true);

        Map<String, Object> claims = new HashMap<>();
        claims.put("merchantTxnId", "BO-TEST-1001");
        claims.put("status", "COMPLETED");
        claims.put("gid", "GID-123");
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("totalAmount", "999.00"); // Expected 5000.00
        paymentData.put("txnCurrency", "INR");
        claims.put("paymentData", paymentData);

        when(cryptoService.parseJwsClaims(validToken)).thenReturn(claims);
        when(bulkOrderRepository.findByBulkOrderNumber("BO-TEST-1001")).thenReturn(Optional.of(testOrder));

        assertThrows(BadRequestException.class, () -> {
            payGlocalService.handleWebhook(null, validToken);
        });

        assertEquals(PaymentStatus.PENDING, testOrder.getPaymentStatus());
        verify(bulkOrderRepository, never()).save(any());
    }

    @Test
    void handleWebhook_IdempotentWhenAlreadyPaid() {
        testOrder.setPaymentStatus(PaymentStatus.PAID);
        testOrder.setOrderStatus(OrderStatus.CONFIRMED);

        String validToken = "valid.header.signature";
        when(cryptoService.verifyJwsToken(validToken)).thenReturn(true);

        Map<String, Object> claims = new HashMap<>();
        claims.put("merchantTxnId", "BO-TEST-1001");
        claims.put("status", "COMPLETED");
        claims.put("gid", "GID-123");

        when(cryptoService.parseJwsClaims(validToken)).thenReturn(claims);
        when(bulkOrderRepository.findByBulkOrderNumber("BO-TEST-1001")).thenReturn(Optional.of(testOrder));

        payGlocalService.handleWebhook(null, validToken);

        // Does not re-save or re-send invoice
        verify(bulkOrderRepository, never()).save(any());
    }

    @Test
    void handleCallback_MissingOrInvalidSignature_RedirectsToError() {
        // Missing token
        Map<String, String> formMissing = new HashMap<>();
        formMissing.put("merchantTxnId", "BO-TEST-1001");
        formMissing.put("status", "COMPLETED");

        String redirectUrl = payGlocalService.handleCallback(formMissing);
        assertTrue(redirectUrl.contains("error=missing_signature"));
        verify(bulkOrderRepository, never()).save(any());

        // Invalid token
        Map<String, String> formInvalid = new HashMap<>();
        formInvalid.put("x-gl-token", "fake.token");
        when(cryptoService.verifyJwsToken("fake.token")).thenReturn(false);

        String redirectInvalid = payGlocalService.handleCallback(formInvalid);
        assertTrue(redirectInvalid.contains("error=invalid_signature"));
        verify(bulkOrderRepository, never()).save(any());
    }

    @Test
    void quickPayment_handleCallback_MissingOrInvalidSignature_RedirectsToError() {
        // Missing token
        Map<String, String> formMissing = new HashMap<>();
        formMissing.put("merchantTxnId", "QP-12345");
        formMissing.put("status", "COMPLETED");

        String redirectUrl = quickPaymentService.handleCallback(formMissing);
        assertTrue(redirectUrl.contains("error=missing_signature"));
        verify(quickPaymentRepository, never()).save(any());

        // Invalid token
        Map<String, String> formInvalid = new HashMap<>();
        formInvalid.put("x-gl-token", "bad.token");
        when(cryptoService.verifyJwsToken("bad.token")).thenReturn(false);

        String redirectInvalid = quickPaymentService.handleCallback(formInvalid);
        assertTrue(redirectInvalid.contains("error=invalid_signature"));
        verify(quickPaymentRepository, never()).save(any());
    }
}

