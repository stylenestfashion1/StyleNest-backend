package com.stylenest.stylenest_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductImage;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Role;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.ProductImageRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.service.EmailService;

/**
 * Full-stack tests (real Spring Security filter chain, real H2 database)
 * covering the 401 vs 403 fix, the empty-cart checkout fix, the product
 * thumbnailUrl field, and the new profile endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

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

    @Autowired
    private ProductImageRepository productImageRepository;

    @MockitoBean
    private EmailService emailService;

    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {

        User customer = User.builder()
                .fullName("Test Customer")
                .email("customer@test.com")
                .password(passwordEncoder.encode("Password123"))
                .phone("9999999999")
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(customer);

        UserDetails customerDetails = userDetailsService.loadUserByUsername(customer.getEmail());
        customerToken = jwtService.generateToken(customerDetails, Role.CUSTOMER);

        User admin = User.builder()
                .fullName("Test Admin")
                .email("admin@test.com")
                .password(passwordEncoder.encode("Password123"))
                .phone("9999999998")
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);

        UserDetails adminDetails = userDetailsService.loadUserByUsername(admin.getEmail());
        adminToken = jwtService.generateToken(adminDetails, Role.ADMIN);
    }

    @Test
    void publicEndpoint_withGarbageToken_stillReturns200() throws Exception {

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer this-is-not-a-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withNoToken_returns401WithApiResponseBody() throws Exception {

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer this-is-not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminEndpoint_withValidCustomerToken_returns403WithApiResponseBody() throws Exception {

        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void checkout_withEmptyCart_returns200WithZeroedResponse() throws Exception {

        mockMvc.perform(get("/api/checkout")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andExpect(jsonPath("$.data.shippingAddress").isEmpty());
    }

    @Test
    void productListing_includesThumbnailUrlFromFirstVariantImage() throws Exception {

        Category category = categoryRepository.save(
                Category.builder().gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).name("Dresses").slug("dresses").build());

        Product product = productRepository.save(
                Product.builder()
                        .name("Floral Dress")
                        .slug("floral-dress")
                        .price(BigDecimal.valueOf(1499))
                        .category(category)
                        .build());

        ProductVariant variant = productVariantRepository.save(
                ProductVariant.builder()
                        .product(product)
                        .color("RED")
                        .size(Size.M)
                        .stock(5)
                        .build());

        productImageRepository.save(
                ProductImage.builder()
                        .product(product)
                        .color(variant.getColor())
                        .imageUrl("https://cdn.example.com/floral-dress-2.jpg")
                        .displayOrder(2)
                        .build());

        productImageRepository.save(
                ProductImage.builder()
                        .product(product)
                        .color(variant.getColor())
                        .imageUrl("https://cdn.example.com/floral-dress-1.jpg")
                        .displayOrder(1)
                        .build());

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl")
                        .value("https://cdn.example.com/floral-dress-1.jpg"));
    }

    @Test
    void wishlistImageUrl_matchesProductThumbnailUrlForSameProduct() throws Exception {

        Category category = categoryRepository.save(
                Category.builder().gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).name("Dresses").slug("dresses-wl").build());

        Product product = productRepository.save(
                Product.builder()
                        .name("Rose Wrap Midi Dress")
                        .slug("rose-wrap-midi-dress")
                        .price(BigDecimal.valueOf(3499))
                        .category(category)
                        .build());

        // First variant persisted has NO images -- this reproduces the
        // production bug where the wishlist mapper naively grabbed
        // product.getVariants().get(0), which can be a variant with no
        // images at all depending on unordered collection iteration.
        productVariantRepository.save(
                ProductVariant.builder()
                        .product(product)
                        .color("BLACK")
                        .size(Size.L)
                        .stock(8)
                        .build());

        ProductVariant variantWithImages = productVariantRepository.save(
                ProductVariant.builder()
                        .product(product)
                        .color("BLACK")
                        .size(Size.M)
                        .stock(15)
                        .build());

        productImageRepository.save(
                ProductImage.builder()
                        .product(product)
                        .color(variantWithImages.getColor())
                        .imageUrl("https://images.unsplash.com/photo-rose-1.jpg")
                        .displayOrder(1)
                        .build());

        productImageRepository.save(
                ProductImage.builder()
                        .product(product)
                        .color(variantWithImages.getColor())
                        .imageUrl("https://images.unsplash.com/photo-rose-2.jpg")
                        .displayOrder(2)
                        .build());

        String productResponse = mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl")
                        .value("https://images.unsplash.com/photo-rose-1.jpg"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + product.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].imageUrl")
                        .value("https://images.unsplash.com/photo-rose-1.jpg"));

        String wishlistResponse = mockMvc.perform(get("/api/wishlist")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].imageUrl")
                        .value("https://images.unsplash.com/photo-rose-1.jpg"))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        String thumbnailUrl = objectMapper.readTree(productResponse)
                .path("data").path("thumbnailUrl").asText();
        String wishlistImageUrl = objectMapper.readTree(wishlistResponse)
                .path("data").path("items").get(0).path("imageUrl").asText();

        assertThat(wishlistImageUrl)
                .as("WishlistItemResponse.imageUrl must equal ProductResponse.thumbnailUrl for the same product")
                .isEqualTo(thumbnailUrl)
                .isNotBlank();
    }

    @Test
    void unmatchedRoute_returns404NotAGeneric500() throws Exception {

        // This path matches no controller mapping at all -- confirms
        // NoHandlerFoundException is caught by GlobalExceptionHandler and
        // turned into a clean 404 rather than a generic 500. Authenticated
        // so the request clears the security filter chain (which would
        // otherwise 401 first) and actually reaches routing.
        mockMvc.perform(get("/api/this-route-does-not-exist")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void wrongHttpMethodOnExistingRoute_returns405() throws Exception {

        // DELETE /api/categories (no id) is not mapped -- only
        // GET/POST /api/categories and DELETE /api/categories/{id} exist.
        mockMvc.perform(delete("/api/categories")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getProfile_returnsCurrentUser() throws Exception {

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("customer@test.com"))
                .andExpect(jsonPath("$.data.fullName").value("Test Customer"));
    }

    @Test
    void updateProfile_updatesFullNameAndPhone() throws Exception {

        String payload = """
                {"fullName":"Updated Name","phone":"8888888888"}
                """;

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.data.phone").value("8888888888"));
    }

    @Test
    void guestTrackingEndpoint_reachableWithNoTokenAtAll_confirmsPermitAll() throws Exception {

        // A bogus lookup still proves the request was NOT rejected at the
        // security-filter level (which would be a 401) -- it reaches the
        // controller/service and gets a clean, auth-independent 404.
        mockMvc.perform(post("/api/guest/orders/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNumber\":\"F21-DOES-NOT-EXIST\",\"phone\":\"0000000000\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCannotAccessAnotherCustomersInvoice() throws Exception {

        Category category = categoryRepository.save(Category.builder().gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).name("Sec Test").slug("sec-test").build());

        Product product = productRepository.save(Product.builder()
                .name("Security Test Product").slug("security-test-product")
                .price(java.math.BigDecimal.TEN).category(category).build());

        ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                .product(product).color("BLUE").size(Size.S).stock(10).build());

        // Order is placed as a guest to avoid needing an authenticated
        // cart/address round trip -- what's under test here is purely the
        // *customer* ownership check on GET /api/orders/{id}/invoice.
        String placeResponse = mockMvc.perform(post("/api/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "guestEmail": "invoice-owner@example.com",
                                  "paymentMethod": "COD",
                                  "currency": "INR",
                                  "shippingAddress": {
                                    "fullName": "Invoice Owner", "phone": "9990001111",
                                    "addressLine1": "1 Owner Rd", "city": "Ownerville",
                                    "state": "OS", "postalCode": "600001", "country": "India"
                                  },
                                  "items": [{"productVariantId": %d, "quantity": 1}]
                                }
                                """.formatted(variant.getId())))
                .andReturn().getResponse().getContentAsString();

        Long orderId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(placeResponse).path("data").path("id").asLong();

        // A registered customer (not the guest who placed it, and not an
        // admin) must not be able to fetch this order's invoice via the
        // authenticated customer endpoint.
        mockMvc.perform(get("/api/orders/" + orderId + "/invoice")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }
}