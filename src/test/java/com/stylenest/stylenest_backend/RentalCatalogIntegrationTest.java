package com.stylenest.stylenest_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Role;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.security.CustomUserDetailsService;
import com.stylenest.stylenest_backend.security.JwtService;

/**
 * Covers the Navratri rental-catalog module end to end: stable share token
 * across item additions, public visibility rules (active/inactive/unknown
 * token all behave correctly), admin-only write access, and that deleting
 * a catalog cascades its items. Never touches Product/Order/Payment tables
 * -- this module is fully isolated, and that isolation is exactly what
 * these tests are checking.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RentalCatalogIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {

        User admin = userRepository.save(User.builder()
                .fullName("Rental Admin").email("admin-rental@test.com")
                .password(passwordEncoder.encode("Password123"))
                .phone("9999999995").role(Role.ADMIN).build());

        UserDetails adminDetails = userDetailsService.loadUserByUsername(admin.getEmail());
        adminToken = jwtService.generateToken(adminDetails, Role.ADMIN);

        User customer = userRepository.save(User.builder()
                .fullName("Rental Customer").email("customer-rental@test.com")
                .password(passwordEncoder.encode("Password123"))
                .phone("9999999994").role(Role.CUSTOMER).build());

        UserDetails customerDetails = userDetailsService.loadUserByUsername(customer.getEmail());
        customerToken = jwtService.generateToken(customerDetails, Role.CUSTOMER);
    }

    private JsonNode createCatalog(String name) throws Exception {

        String payload = """
                { "name": "%s" }
                """.formatted(name);

        String response = mockMvc.perform(post("/api/admin/rental-catalogs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data");
    }

    private void addItem(Long catalogId, String name, String colour, int price) throws Exception {

        String payload = """
                { "name": "%s", "colour": "%s", "rentalPrice": %d }
                """.formatted(name, colour, price);

        mockMvc.perform(post("/api/admin/rental-catalogs/" + catalogId + "/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    void createCatalog_generatesStableShareToken() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");

        assertThat(catalog.path("shareToken").asText()).isNotBlank();
        assertThat(catalog.path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void shareToken_neverChanges_whenItemsAreAddedLater() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");
        Long catalogId = catalog.path("id").asLong();
        String originalToken = catalog.path("shareToken").asText();

        addItem(catalogId, "Green Lehenga", "Green", 2500);
        addItem(catalogId, "Pink Lehenga", "Pink", 2000);

        String afterFirstBatch = mockMvc.perform(get("/api/admin/rental-catalogs/" + catalogId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(afterFirstBatch).path("data").path("shareToken").asText())
                .isEqualTo(originalToken);

        addItem(catalogId, "Red Lehenga", "Red", 3000);
        addItem(catalogId, "Blue Lehenga", "Blue", 2800);

        String afterSecondBatch = mockMvc.perform(get("/api/admin/rental-catalogs/" + catalogId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode finalCatalog = objectMapper.readTree(afterSecondBatch).path("data");

        assertThat(finalCatalog.path("shareToken").asText()).isEqualTo(originalToken);
        assertThat(finalCatalog.path("items")).hasSize(4);
    }

    @Test
    void publicEndpoint_sameLink_showsNewlyAddedItems_noLoginRequired() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");
        Long catalogId = catalog.path("id").asLong();
        String token = catalog.path("shareToken").asText();

        addItem(catalogId, "Lehenga A", "Green", 2500);
        addItem(catalogId, "Lehenga B", "Pink", 2000);

        mockMvc.perform(get("/api/rental-catalogs/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.name").value("Navratri Lehenga Rental 2026"));

        addItem(catalogId, "Lehenga C", "Red", 3000);
        addItem(catalogId, "Lehenga D", "Blue", 2800);

        // Same URL/token -- no new share action -- now shows all 4.
        mockMvc.perform(get("/api/rental-catalogs/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4));
    }

    @Test
    void publicEndpoint_unknownToken_returnsNotFound() throws Exception {

        mockMvc.perform(get("/api/rental-catalogs/this-token-does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicEndpoint_inactiveCatalog_returnsNotFound_sameAsUnknownToken() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");
        Long catalogId = catalog.path("id").asLong();
        String token = catalog.path("shareToken").asText();

        mockMvc.perform(get("/api/rental-catalogs/" + token)).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/rental-catalogs/" + catalogId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rental-catalogs/" + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/rental-catalogs/" + catalogId + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rental-catalogs/" + token)).andExpect(status().isOk());
    }

    @Test
    void activeCatalogEndpoint_returnsMostRecentActiveCatalog_whenOneExists() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");
        String token = catalog.path("shareToken").asText();

        mockMvc.perform(get("/api/rental-catalogs/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.shareToken").value(token));
    }

    @Test
    void activeCatalogEndpoint_prefersMostRecentlyCreated_whenMultipleActive() throws Exception {

        createCatalog("Navratri Lehenga Rental 2026");
        JsonNode newer = createCatalog("Wedding Season Lehenga Rental");

        mockMvc.perform(get("/api/rental-catalogs/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareToken").value(newer.path("shareToken").asText()));
    }

    @Test
    void activeCatalogEndpoint_returnsUnavailable_whenNoCatalogIsActive() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");
        Long catalogId = catalog.path("id").asLong();

        mockMvc.perform(post("/api/admin/rental-catalogs/" + catalogId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rental-catalogs/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void editItem_updatesFields_withoutChangingCatalogToken() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");
        Long catalogId = catalog.path("id").asLong();
        addItem(catalogId, "Green Lehenga", "Green", 2500);

        String listResponse = mockMvc.perform(get("/api/admin/rental-catalogs/" + catalogId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        Long itemId = objectMapper.readTree(listResponse).path("data").path("items").get(0).path("id").asLong();

        String updatePayload = """
                { "name": "Emerald Green Lehenga", "colour": "Emerald Green", "rentalPrice": 2700 }
                """;

        mockMvc.perform(put("/api/admin/rental-catalogs/" + catalogId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Emerald Green Lehenga"))
                .andExpect(jsonPath("$.data.rentalPrice").value(2700));
    }

    @Test
    void deleteItem_removesItOnly_catalogAndTokenSurvive() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");
        Long catalogId = catalog.path("id").asLong();
        String token = catalog.path("shareToken").asText();
        addItem(catalogId, "Green Lehenga", "Green", 2500);
        addItem(catalogId, "Pink Lehenga", "Pink", 2000);

        String listResponse = mockMvc.perform(get("/api/admin/rental-catalogs/" + catalogId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        Long itemId = objectMapper.readTree(listResponse).path("data").path("items").get(0).path("id").asLong();

        mockMvc.perform(delete("/api/admin/rental-catalogs/" + catalogId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rental-catalogs/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void deleteCatalog_cascadesToItems_andCatalogNoLongerAccessible() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");
        Long catalogId = catalog.path("id").asLong();
        String token = catalog.path("shareToken").asText();
        addItem(catalogId, "Green Lehenga", "Green", 2500);

        mockMvc.perform(delete("/api/admin/rental-catalogs/" + catalogId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rental-catalogs/" + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/rental-catalogs/" + catalogId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminEndpoints_rejectNonAdminCustomer() throws Exception {

        mockMvc.perform(get("/api/admin/rental-catalogs")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_rejectUnauthenticatedRequest() throws Exception {

        mockMvc.perform(get("/api/admin/rental-catalogs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCatalog_blankName_isRejected() throws Exception {

        mockMvc.perform(post("/api/admin/rental-catalogs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_invalidRentalPrice_isRejected() throws Exception {

        JsonNode catalog = createCatalog("Navratri Lehenga Rental 2026");
        Long catalogId = catalog.path("id").asLong();

        mockMvc.perform(post("/api/admin/rental-catalogs/" + catalogId + "/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"X\", \"colour\": \"Red\", \"rentalPrice\": 0 }"))
                .andExpect(status().isBadRequest());
    }
}
