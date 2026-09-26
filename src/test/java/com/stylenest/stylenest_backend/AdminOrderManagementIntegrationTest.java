package com.stylenest.stylenest_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Role;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.security.CustomUserDetailsService;
import com.stylenest.stylenest_backend.security.JwtService;
import com.stylenest.stylenest_backend.service.EmailService;

/**
 * Full-stack coverage for admin order search + shipment management, and
 * for the mixed guest/registered order visibility requirement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminOrderManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @MockitoBean
    private EmailService emailService;

    private String adminToken;
    private String customerToken;
    private ProductVariant variant;

    @BeforeEach
    void setUp() throws Exception {

        User admin = userRepository.save(User.builder()
                .fullName("Admin").email("admin-orders@test.com")
                .password(passwordEncoder.encode("Password123"))
                .phone("9999999997").role(Role.ADMIN).build());

        UserDetails adminDetails = userDetailsService.loadUserByUsername(admin.getEmail());
        adminToken = jwtService.generateToken(adminDetails, Role.ADMIN);

        User customer = userRepository.save(User.builder()
                .fullName("Customer").email("customer-orders@test.com")
                .password(passwordEncoder.encode("Password123"))
                .phone("9999999996").role(Role.CUSTOMER).build());

        UserDetails customerDetails = userDetailsService.loadUserByUsername(customer.getEmail());
        customerToken = jwtService.generateToken(customerDetails, Role.CUSTOMER);

        Category category = categoryRepository.save(Category.builder().gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).name("Admin Test").slug("admin-test").build());

        Product product = productRepository.save(Product.builder()
                .name("Admin Test Product").slug("admin-test-product")
                .price(new BigDecimal("1000.00")).category(category).build());

        variant = productVariantRepository.save(ProductVariant.builder()
                .product(product).color("RED").size(Size.L).stock(10).build());
    }

    private Long placeGuestOrder(String guestEmail) throws Exception {

        String payload = """
                {
                  "guestEmail": "%s",
                  "paymentMethod": "COD",
                  "currency": "INR",
                  "shippingAddress": {
                    "fullName": "Search Guest", "phone": "9991112222",
                    "addressLine1": "1 Search Rd", "city": "Searchville",
                    "state": "SS", "postalCode": "500001", "country": "India"
                  },
                  "items": [{"productVariantId": %d, "quantity": 1}]
                }
                """.formatted(guestEmail, variant.getId());

        String response = mockMvc.perform(post("/api/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return new ObjectMapper().readTree(response).path("data").path("id").asLong();
    }

    @Test
    void searchOrders_byGuestEmail_findsGuestOrder() throws Exception {

        placeGuestOrder("findme-search@example.com");

        mockMvc.perform(get("/api/admin/orders/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "findme-search@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].customerEmail").value("findme-search@example.com"))
                .andExpect(jsonPath("$.data.content[0].isGuest").value(true));
    }

    @Test
    void searchOrders_byOrderNumber_findsOrder() throws Exception {

        Long orderId = placeGuestOrder("ordernum-search@example.com");

        String orderResponse = mockMvc.perform(get("/api/admin/orders/" + orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();

        String orderNumber = new ObjectMapper().readTree(orderResponse).path("data").path("orderNumber").asText();

        mockMvc.perform(get("/api/admin/orders/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", orderNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(orderNumber));
    }

    @Test
    void searchOrders_withoutAdminRole_returns403() throws Exception {

        mockMvc.perform(get("/api/admin/orders/search")
                        .header("Authorization", "Bearer " + customerToken)
                        .param("keyword", "anything"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shipmentUpdate_validTransition_updatesStatusAndRecordsHistory() throws Exception {

        Long orderId = placeGuestOrder("shipment-test@example.com");

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/shipment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentStatus":"PACKED","trackingNumber":"TRK123","courierName":"Test Courier"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipmentStatus").value("PACKED"))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK123"))
                .andExpect(jsonPath("$.data.history").isArray());
    }

    @Test
    void shipmentUpdate_invalidBackwardTransition_returns400() throws Exception {

        Long orderId = placeGuestOrder("shipment-backward@example.com");

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/shipment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentStatus\":\"DELIVERED\"}"))
                .andExpect(status().isOk()); // PROCESSING -> DELIVERED is a valid skip-ahead

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/shipment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentStatus\":\"PACKED\"}")) // backward from DELIVERED
                .andExpect(status().isBadRequest());
    }

    @Test
    void shipmentUpdate_withoutAdminRole_returns403() throws Exception {

        Long orderId = placeGuestOrder("shipment-forbidden@example.com");

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/shipment")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentStatus\":\"PACKED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanViewInvoiceForAnyOrder() throws Exception {

        Long orderId = placeGuestOrder("admin-invoice@example.com");

        mockMvc.perform(get("/api/admin/orders/" + orderId + "/invoice/pdf")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
                        .isEqualTo(MediaType.APPLICATION_PDF_VALUE));
    }
}
