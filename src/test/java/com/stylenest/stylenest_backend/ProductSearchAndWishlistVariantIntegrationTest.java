package com.stylenest.stylenest_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.stylenest.stylenest_backend.security.CustomUserDetailsService;
import com.stylenest.stylenest_backend.security.JwtService;

/**
 * Full-stack tests mirroring the real "Shoowel" product reported live:
 * variants RED/L, BLACK/M, BLACK/XS, no RED/M and no BLACK/L variant.
 * Images are keyed by (product, color) -- BLACK/M and BLACK/XS therefore
 * share one BLACK image set, RED/L has its own RED image set. Covers the
 * combined color+size filter fix (B.1), filter-aware thumbnailUrl (B.2),
 * availableColors (B.3), and wishlist variant-aware images (Part A).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductSearchAndWishlistVariantIntegrationTest {

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

    private Long shoowelId;
    private Long redLVariantId;
    private Long blackMVariantId;
    private Long blackXsVariantId;

    private static final String RED_IMAGE = "https://pashtush.in/cdn/shop/products/red-shawl.jpg";
    private static final String BLACK_IMAGE = "https://pashtush.in/cdn/shop/products/pashtush-mens.jpg";

    private String newCustomerToken(String email) {

        User customer = User.builder()
                .fullName("Test Customer " + email)
                .email(email)
                .password(passwordEncoder.encode("Password123"))
                .phone("9999999999")
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(customer);

        UserDetails userDetails = userDetailsService.loadUserByUsername(customer.getEmail());
        return jwtService.generateToken(userDetails, Role.CUSTOMER);
    }

    @BeforeEach
    void setUp() {

        Category category = categoryRepository.save(
                Category.builder().gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).name("Winters").slug("winters-test").build());

        Product shoowel = productRepository.save(
                Product.builder()
                        .name("Shoowel")
                        .slug("shoowel-test")
                        .price(BigDecimal.valueOf(2588))
                        .category(category)
                        .build());
        shoowelId = shoowel.getId();

        // Insertion order matters for the "no filter" default-resolution
        // test below: RED image saved first, so RED is the lowest-order
        // color when no filter prefers one.
        ProductVariant redL = productVariantRepository.save(
                ProductVariant.builder().product(shoowel).color("RED").size(Size.L).stock(3).build());
        ProductVariant blackM = productVariantRepository.save(
                ProductVariant.builder().product(shoowel).color("BLACK").size(Size.M).stock(5).build());
        ProductVariant blackXs = productVariantRepository.save(
                ProductVariant.builder().product(shoowel).color("BLACK").size(Size.XS).stock(3).build());

        redLVariantId = redL.getId();
        blackMVariantId = blackM.getId();
        blackXsVariantId = blackXs.getId();

        // One image set per color -- shared by every size of that color.
        productImageRepository.save(
                ProductImage.builder().product(shoowel).color("RED").imageUrl(RED_IMAGE).displayOrder(1).build());
        productImageRepository.save(
                ProductImage.builder().product(shoowel).color("BLACK").imageUrl(BLACK_IMAGE).displayOrder(1).build());
    }

    private String searchPayload(String color, String size) {
        StringBuilder sb = new StringBuilder("{");
        if (color != null) sb.append("\"color\":\"").append(color).append("\",");
        if (size != null) sb.append("\"size\":\"").append(size).append("\",");
        sb.append("\"page\":0,\"sizePerPage\":12}");
        return sb.toString();
    }

    // ================= Regression: "active" filter =================
    //
    // A prior fix to the color+size filter must not affect the
    // independent "active" predicate, which is applied directly on the
    // product root and has nothing to do with the variant join.

    @Test
    void search_activeTrueOnly_returnsActiveProduct_noOtherFilters() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"page\":0,\"sizePerPage\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isNotEmpty());
    }

    @Test
    void search_activeFalse_excludesActiveProduct() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"page\":0,\"sizePerPage\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isEmpty());
    }

    @Test
    void search_activeTrueCombinedWithColorAndSize_stillWorksTogether() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"color\":\"RED\",\"size\":\"M\",\"page\":0,\"sizePerPage\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isEmpty());

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"color\":\"RED\",\"size\":\"L\",\"page\":0,\"sizePerPage\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isNotEmpty());
    }

    // ================= B.1: combined color+size filter =================

    @Test
    void search_colorRedSizeM_doesNotMatchShoowel_noVariantHasBoth() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload("RED", "M")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isEmpty());
    }

    @Test
    void search_colorBlackSizeL_doesNotMatchShoowel_noVariantHasBoth() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload("BLACK", "L")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isEmpty());
    }

    @Test
    void search_colorRedSizeL_matchesShoowel_realVariantCombo() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload("RED", "L")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isNotEmpty());
    }

    @Test
    void search_colorBlackSizeXs_matchesShoowel_realVariantCombo() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload("BLACK", "XS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isNotEmpty());
    }

    @Test
    void search_colorOnly_stillMatchesShoowel_singleFieldUnaffected() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload("RED", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isNotEmpty());
    }

    @Test
    void search_sizeOnly_stillMatchesShoowel_singleFieldUnaffected() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload(null, "M")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")]").isNotEmpty());
    }

    // ================= B.2: filter-aware thumbnailUrl =================
    //
    // Images are keyed by (product, color) now -- size no longer
    // influences which image is chosen, only color does.

    @Test
    void search_noFilter_thumbnailIsDefaultLowestColorVariant() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload(null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")].thumbnailUrl")
                        .value(org.hamcrest.Matchers.contains(BLACK_IMAGE)));
    }

    @Test
    void search_colorRed_thumbnailIsRedVariantImage_notDefault() throws Exception {

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload("RED", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")].thumbnailUrl")
                        .value(org.hamcrest.Matchers.contains(RED_IMAGE)));
    }

    @Test
    void search_sizeM_thumbnailIsBlackVariantImage_sizeDoesNotChangeColorImage() throws Exception {

        // BLACK/M and BLACK/XS share the same BLACK image set -- filtering
        // by size M (which only BLACK has) must still resolve to the
        // BLACK image, exactly as filtering by size XS would.
        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload(null, "M")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + shoowelId + ")].thumbnailUrl")
                        .value(org.hamcrest.Matchers.contains(BLACK_IMAGE)));
    }

    // ================= B.3: availableColors =================

    @Test
    void search_availableColors_returnsDistinctColorsForShoowel() throws Exception {

        mockMvc.perform(get("/api/products/" + shoowelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableColors")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("BLACK", "RED")));
    }

    @Test
    void getProductById_productWithNoVariants_returnsEmptyAvailableColorsNotNull() throws Exception {

        Category category = categoryRepository.save(
                Category.builder().gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).name("Empty Cat").slug("empty-cat-test").build());

        Product noVariantProduct = productRepository.save(
                Product.builder()
                        .name("No Variant Product")
                        .slug("no-variant-product-test")
                        .price(BigDecimal.valueOf(100))
                        .category(category)
                        .build());

        mockMvc.perform(get("/api/products/" + noVariantProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableColors").isArray())
                .andExpect(jsonPath("$.data.availableColors").isEmpty())
                .andExpect(jsonPath("$.data.thumbnailUrl").isEmpty());
    }

    // ================= Part A: wishlist variant-aware image =================

    @Test
    void wishlist_addWithRedLVariant_showsRedVariantImageAndColorSize() throws Exception {

        String token = newCustomerToken("wl_variant_red@example.com");

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + redLVariantId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].imageUrl").value(RED_IMAGE))
                .andExpect(jsonPath("$.data.items[0].color").value("RED"))
                .andExpect(jsonPath("$.data.items[0].size").value("L"))
                .andExpect(jsonPath("$.data.items[0].productVariantId").value(redLVariantId));

        mockMvc.perform(get("/api/wishlist").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].imageUrl").value(RED_IMAGE))
                .andExpect(jsonPath("$.data.items[0].color").value("RED"))
                .andExpect(jsonPath("$.data.items[0].size").value("L"));
    }

    @Test
    void wishlist_addWithBlackXsVariant_showsBlackVariantImageAndColorSize() throws Exception {

        String token = newCustomerToken("wl_variant_black@example.com");

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + blackXsVariantId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].imageUrl").value(BLACK_IMAGE))
                .andExpect(jsonPath("$.data.items[0].color").value("BLACK"))
                .andExpect(jsonPath("$.data.items[0].size").value("XS"));

        mockMvc.perform(get("/api/wishlist").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].imageUrl").value(BLACK_IMAGE));
    }

    @Test
    void wishlist_addWithoutVariant_fallsBackToProductDefaultThumbnail() throws Exception {

        String token = newCustomerToken("wl_variant_none@example.com");

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].imageUrl").value(BLACK_IMAGE))
                .andExpect(jsonPath("$.data.items[0].color").isEmpty())
                .andExpect(jsonPath("$.data.items[0].size").isEmpty())
                .andExpect(jsonPath("$.data.items[0].productVariantId").isEmpty());
    }

    @Test
    void wishlist_reAddWithDifferentVariant_createsSeparateItem_bothPresent() throws Exception {

        String token = newCustomerToken("wl_variant_reAdd@example.com");

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + redLVariantId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + blackMVariantId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(get("/api/wishlist").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[?(@.productVariantId == " + redLVariantId + ")].imageUrl")
                        .value(org.hamcrest.Matchers.contains(RED_IMAGE)))
                .andExpect(jsonPath("$.data.items[?(@.productVariantId == " + blackMVariantId + ")].imageUrl")
                        .value(org.hamcrest.Matchers.contains(BLACK_IMAGE)));
    }

    @Test
    void wishlist_reAddSameVariantTwice_isIdempotent_notThree() throws Exception {

        String token = newCustomerToken("wl_variant_idempotent@example.com");

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + redLVariantId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + blackMVariantId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // Re-add RED/L a second time -- still 2 items, not 3.
        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + redLVariantId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(get("/api/wishlist").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void wishlist_removeOneVariantEntry_doesNotAffectSiblingVariantEntry() throws Exception {

        String token = newCustomerToken("wl_variant_remove@example.com");

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + redLVariantId + "}"))
                .andExpect(status().isCreated());

        String afterSecondAdd = mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + blackMVariantId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode items = objectMapper.readTree(afterSecondAdd).path("data").path("items");
        long redItemId = -1;
        for (com.fasterxml.jackson.databind.JsonNode item : items) {
            if (item.path("productVariantId").asLong() == redLVariantId) {
                redItemId = item.path("wishlistItemId").asLong();
            }
        }
        org.assertj.core.api.Assertions.assertThat(redItemId).isNotEqualTo(-1);

        mockMvc.perform(delete("/api/wishlist/items/" + redItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/wishlist").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productVariantId").value(blackMVariantId.intValue()))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value(BLACK_IMAGE));
    }

    @Test
    void wishlist_legacyNoVariantAdd_createsSeparateGenericEntry_alongsideVariantSpecificOnes() throws Exception {

        String token = newCustomerToken("wl_variant_legacy@example.com");

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + redLVariantId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        // Legacy call, no variant context at all.
        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(get("/api/wishlist").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[?(@.productVariantId == null)]").isNotEmpty())
                .andExpect(jsonPath("$.data.items[?(@.productVariantId == " + redLVariantId + ")]").isNotEmpty());
    }

    @Test
    void wishlist_addWithVariantNotBelongingToProduct_isRejected() throws Exception {

        String token = newCustomerToken("wl_variant_mismatch@example.com");

        Category otherCategory = categoryRepository.save(
                Category.builder().gender(com.stylenest.stylenest_backend.enums.Gender.WOMEN).name("Other").slug("other-cat-test").build());
        Product otherProduct = productRepository.save(
                Product.builder()
                        .name("Other Product")
                        .slug("other-product-test")
                        .price(BigDecimal.valueOf(500))
                        .category(otherCategory)
                        .build());
        ProductVariant otherVariant = productVariantRepository.save(
                ProductVariant.builder().product(otherProduct).color("GREEN").size(Size.S).stock(1).build());

        mockMvc.perform(post("/api/wishlist/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + shoowelId + ",\"productVariantId\":" + otherVariant.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
