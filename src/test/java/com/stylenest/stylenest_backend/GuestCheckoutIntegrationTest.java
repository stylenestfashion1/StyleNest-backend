package com.stylenest.stylenest_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.service.EmailService;

/**
 * Full-stack coverage for guest checkout (no login/registration/OTP): place
 * order, secure order-number+phone tracking, phone-gated invoice download.
 * EmailService is mocked so these tests never make a real SMTP send.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestCheckoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @MockitoBean
    private EmailService emailService;

    private ProductVariant variant;

    @BeforeEach
    void setUp() {

        Category category = categoryRepository.save(Category.builder().gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).name("Dresses").slug("guest-dresses").build());

        Product product = productRepository.save(Product.builder()
                .name("Guest Test Dress")
                .slug("guest-test-dress")
                .price(new BigDecimal("1500.00"))
                .category(category)
                .build());

        variant = productVariantRepository.save(ProductVariant.builder()
                .product(product)
                .color("BLACK")
                .size(Size.M)
                .stock(5)
                .build());
    }

    private String guestOrderPayload() {
        return """
                {
                  "guestEmail": "guest@example.com",
                  "paymentMethod": "COD",
                  "shippingAddress": {
                    "fullName": "Guest Customer",
                    "phone": "9998887777",
                    "phoneCountryCode": "+91",
                    "addressLine1": "123 Guest St",
                    "city": "Metropolis",
                    "state": "State",
                    "postalCode": "100001",
                    "country": "India",
                    "countryCode": "IN"
                  },
                  "items": [{"productVariantId": %d, "quantity": 2}]
                }
                """.formatted(variant.getId());
    }

    @Test
    void placeGuestOrder_cod_createsOrderAndDecrementsStock() throws Exception {

        String response = mockMvc.perform(post("/api/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestOrderPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.isGuest").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("SN-");

        ProductVariant updated = productVariantRepository.findById(variant.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(3); // 5 - 2
    }

    @Test
    void placeGuestOrder_insufficientStock_returns400() throws Exception {

        String payload = """
                {
                  "guestEmail": "guest@example.com",
                  "paymentMethod": "COD",
                  "shippingAddress": {
                    "fullName": "Guest Customer", "phone": "9998887777",
                    "addressLine1": "123 Guest St", "city": "Metropolis",
                    "state": "State", "postalCode": "100001", "country": "India"
                  },
                  "items": [{"productVariantId": %d, "quantity": 999}]
                }
                """.formatted(variant.getId());

        mockMvc.perform(post("/api/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeGuestOrder_doesNotRequireAuthentication() throws Exception {

        // No Authorization header at all -- must still succeed.
        mockMvc.perform(post("/api/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestOrderPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    void trackOrder_correctPhone_returnsOrder() throws Exception {

        String placeResponse = mockMvc.perform(post("/api/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestOrderPayload()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderNumber = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(placeResponse).path("data").path("orderNumber").asText();

        mockMvc.perform(post("/api/guest/orders/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNumber\":\"" + orderNumber + "\",\"phone\":\"9998887777\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value(orderNumber));
    }

    @Test
    void trackOrder_wrongPhone_returns404() throws Exception {

        String placeResponse = mockMvc.perform(post("/api/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestOrderPayload()))
                .andReturn().getResponse().getContentAsString();

        String orderNumber = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(placeResponse).path("data").path("orderNumber").asText();

        mockMvc.perform(post("/api/guest/orders/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNumber\":\"" + orderNumber + "\",\"phone\":\"0000000000\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInvoice_correctPhone_returnsPdf() throws Exception {

        String placeResponse = mockMvc.perform(post("/api/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestOrderPayload()))
                .andReturn().getResponse().getContentAsString();

        String orderNumber = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(placeResponse).path("data").path("orderNumber").asText();

        mockMvc.perform(get("/api/guest/orders/invoice")
                        .param("orderNumber", orderNumber)
                        .param("phone", "9998887777"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .isEqualTo(MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    void getInvoice_wrongPhone_returns404() throws Exception {

        String placeResponse = mockMvc.perform(post("/api/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestOrderPayload()))
                .andReturn().getResponse().getContentAsString();

        String orderNumber = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(placeResponse).path("data").path("orderNumber").asText();

        mockMvc.perform(get("/api/guest/orders/invoice")
                        .param("orderNumber", orderNumber)
                        .param("phone", "0000000000"))
                .andExpect(status().isNotFound());
    }
}
