package com.stylenest.stylenest_backend.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.config.PayGlocalProperties;
import com.stylenest.stylenest_backend.dto.quickpay.QuickPaymentAddressUpdateRequest;
import com.stylenest.stylenest_backend.dto.quickpay.QuickPaymentResponse;
import com.stylenest.stylenest_backend.entity.QuickPayment;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.repository.QuickPaymentRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.payglocal.PayGlocalCryptoService;

@ExtendWith(MockitoExtension.class)
class QuickPaymentAddressUpdateTest {

    @Mock
    private PayGlocalProperties properties;

    @Mock
    private PayGlocalCryptoService cryptoService;

    @Mock
    private QuickPaymentRepository quickPaymentRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EmailService emailService;

    @Mock
    private RestClient restClient;

    @InjectMocks
    private QuickPaymentServiceImpl quickPaymentService;

    private QuickPayment samplePayment;

    @BeforeEach
    void setUp() {
        samplePayment = QuickPayment.builder()
                .id(1L)
                .paymentNumber("QP-1794260317584-7561")
                .customerName("Keesha Darrington")
                .customerEmail("keesha@example.com")
                .customerPhone("+1 2516107190")
                .addressLine1("6680 Rd")
                .city("Theodore")
                .state("Alabama")
                .postalCode("36582")
                .country("United States")
                .amount(new BigDecimal("300.00"))
                .currency("USD")
                .paymentStatus(PaymentStatus.PAID)
                .build();
    }

    @Test
    @DisplayName("Successfully update quick payment address with valid details")
    void testUpdateAddress_Success() {
        when(quickPaymentRepository.findByPaymentNumber("QP-1794260317584-7561"))
                .thenReturn(Optional.of(samplePayment));
        when(quickPaymentRepository.save(any(QuickPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuickPaymentAddressUpdateRequest request = QuickPaymentAddressUpdateRequest.builder()
                .customerName("Keesha Darrington")
                .customerPhone("+1 2516107190")
                .addressLine1("6680 Old Pascagoula Rd")
                .addressLine2("Apt 4B")
                .city("Theodore")
                .state("Alabama")
                .postalCode("36582")
                .country("United States")
                .build();

        QuickPaymentResponse response = quickPaymentService.updateAddress("QP-1794260317584-7561", request);

        assertNotNull(response);
        assertEquals("6680 Old Pascagoula Rd", response.getAddressLine1());
        assertEquals("Apt 4B", response.getAddressLine2());
        assertEquals("Theodore", response.getCity());
        assertEquals("Alabama", response.getState());
        assertEquals("36582", response.getPostalCode());
        assertEquals("United States", response.getCountry());

        verify(quickPaymentRepository).save(samplePayment);
    }

    @Test
    @DisplayName("Update address throws ResourceNotFoundException when payment number is not found")
    void testUpdateAddress_NotFound() {
        when(quickPaymentRepository.findByPaymentNumber("QP-NONEXISTENT"))
                .thenReturn(Optional.empty());

        QuickPaymentAddressUpdateRequest request = QuickPaymentAddressUpdateRequest.builder()
                .addressLine1("123 Main St")
                .city("Wilmer")
                .postalCode("36587")
                .country("United States")
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                quickPaymentService.updateAddress("QP-NONEXISTENT", request));

        verify(quickPaymentRepository, never()).save(any());
    }
}
