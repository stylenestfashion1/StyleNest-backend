package com.stylenest.stylenest_backend;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.stylenest.stylenest_backend.service.RentalBookingService;

/**
 * Covers the real booking extension on top of the rental catalog: day-count
 * calculation, server-side (never client-trusted) availability/overlap
 * enforcement, the PENDING_PAYMENT -> PAYMENT_SUBMITTED -> CONFIRMED
 * lifecycle, full and partial admin cancellation with the segment-based
 * audit trail, historical price preservation, and admin-only enforcement.
 * Never touches Product/Order/Payment tables -- see spec section 24.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RentalBookingIntegrationTest {

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
    private RentalBookingService rentalBookingService;

    @Autowired
    private com.stylenest.stylenest_backend.repository.RentalBookingRepository rentalBookingRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String customerToken;

    // Inside the default seeded season (11-19 Oct 2026) -- see
    // RentalSettingsServiceImpl's DEFAULT_SEASON_START/END.
    private static final String IN_SEASON_DAY = "2026-10-12";

    @BeforeEach
    void setUp() {

        User admin = userRepository.save(User.builder()
                .fullName("Rental Booking Admin").email("admin-rental-booking@test.com")
                .password(passwordEncoder.encode("Password123"))
                .phone("9999999993").role(Role.ADMIN).build());

        UserDetails adminDetails = userDetailsService.loadUserByUsername(admin.getEmail());
        adminToken = jwtService.generateToken(adminDetails, Role.ADMIN);

        User customer = userRepository.save(User.builder()
                .fullName("Rental Booking Customer").email("customer-rental-booking@test.com")
                .password(passwordEncoder.encode("Password123"))
                .phone("9999999992").role(Role.CUSTOMER).build());

        UserDetails customerDetails = userDetailsService.loadUserByUsername(customer.getEmail());
        customerToken = jwtService.generateToken(customerDetails, Role.CUSTOMER);
    }

    private String createCatalogAndItem(int dailyRate) throws Exception {

        String catalogResponse = mockMvc.perform(post("/api/admin/rental-catalogs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Navratri Lehenga Rental 2026\" }"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode catalog = objectMapper.readTree(catalogResponse).path("data");
        Long catalogId = catalog.path("id").asLong();
        String shareToken = catalog.path("shareToken").asText();

        mockMvc.perform(post("/api/admin/rental-catalogs/" + catalogId + "/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Blue Lehenga\", \"colour\": \"Blue\", \"rentalPrice\": " + dailyRate + " }"))
                .andExpect(status().isCreated());

        return shareToken;
    }

    private Long firstItemId(String shareToken) throws Exception {

        String response = mockMvc.perform(get("/api/rental-catalogs/" + shareToken))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data").path("items").get(0).path("id").asLong();
    }

    private String bookingPayload(String start, String end) {
        return """
                { "startDate": "%s", "endDate": "%s", "customerName": "Rahul", "customerPhone": "9812345678", "termsAccepted": true }
                """.formatted(start, end);
    }

    private JsonNode createBooking(String shareToken, Long itemId, String start, String end) throws Exception {

        String response = mockMvc.perform(post("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(start, end)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data");
    }

    @Test
    void oneDayBooking_calculatesOneDayAndCorrectTotal() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);

        JsonNode booking = createBooking(shareToken, itemId, "2026-10-12", "2026-10-12");

        assertThat(booking.path("rentalDays").asInt()).isEqualTo(1);
        assertThat(booking.path("totalAmount").asInt()).isEqualTo(800);
        assertThat(booking.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(booking.path("bookingReference").asText()).startsWith("RENT-2026-");
    }

    @Test
    void sixDayBooking_calculatesSixDaysAndCorrectTotal() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);

        JsonNode booking = createBooking(shareToken, itemId, "2026-10-11", "2026-10-16");

        assertThat(booking.path("rentalDays").asInt()).isEqualTo(6);
        assertThat(booking.path("totalAmount").asInt()).isEqualTo(4800);
    }

    @Test
    void nineDayBooking_fullSeason_calculatesNineDaysAndCorrectTotal() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);

        JsonNode booking = createBooking(shareToken, itemId, "2026-10-11", "2026-10-19");

        assertThat(booking.path("rentalDays").asInt()).isEqualTo(9);
        assertThat(booking.path("totalAmount").asInt()).isEqualTo(7200);
    }

    @Test
    void booking_outsideConfiguredSeason_isRejected() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);

        mockMvc.perform(post("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload("2026-10-05", "2026-10-08")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void booking_withoutAcceptingTerms_isRejected() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);

        String payload = """
                { "startDate": "2026-10-12", "endDate": "2026-10-12", "customerName": "Rahul", "customerPhone": "9812345678", "termsAccepted": false }
                """;

        mockMvc.perform(post("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overlappingBooking_isRejected_butAdjacentNonOverlappingIsAllowed() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);

        createBooking(shareToken, itemId, "2026-10-11", "2026-10-13");

        // Overlaps (12-15 overlaps the existing 11-13).
        mockMvc.perform(post("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload("2026-10-12", "2026-10-15")))
                .andExpect(status().isConflict());

        // Does not overlap (starts the day after the existing booking ends).
        mockMvc.perform(post("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload("2026-10-14", "2026-10-16")))
                .andExpect(status().isCreated());
    }

    @Test
    void unavailableDates_reflectsActiveBooking() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);

        createBooking(shareToken, itemId, "2026-10-11", "2026-10-13");

        mockMvc.perform(get("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/unavailable-dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableDates.length()").value(3))
                .andExpect(jsonPath("$.data.unavailableDates[0]").value("2026-10-11"))
                .andExpect(jsonPath("$.data.unavailableDates[2]").value("2026-10-13"));
    }

    @Test
    void paymentSubmission_movesBookingToPaymentSubmitted() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);
        JsonNode booking = createBooking(shareToken, itemId, IN_SEASON_DAY, IN_SEASON_DAY);
        String reference = booking.path("bookingReference").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/rental-catalogs/" + shareToken + "/bookings/" + reference + "/payment")
                        .param("transactionId", "TXN123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_SUBMITTED"))
                .andExpect(jsonPath("$.data.transactionId").value("TXN123456"));
    }

    @Test
    void adminConfirm_movesBookingToConfirmed_andDatesStayBlocked() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);
        JsonNode booking = createBooking(shareToken, itemId, IN_SEASON_DAY, IN_SEASON_DAY);
        String reference = booking.path("bookingReference").asText();

        mockMvc.perform(post("/api/admin/rental-bookings/" + reference + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/unavailable-dates"))
                .andExpect(jsonPath("$.data.unavailableDates.length()").value(1));
    }

    @Test
    void adminFullCancellation_releasesAllDates() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);
        JsonNode booking = createBooking(shareToken, itemId, "2026-10-11", "2026-10-19");
        String reference = booking.path("bookingReference").asText();

        mockMvc.perform(post("/api/admin/rental-bookings/" + reference + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/rental-bookings/" + reference + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/unavailable-dates"))
                .andExpect(jsonPath("$.data.unavailableDates.length()").value(0));

        // The full 9-day range is bookable again.
        mockMvc.perform(post("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload("2026-10-11", "2026-10-19")))
                .andExpect(status().isCreated());
    }

    @Test
    void adminPartialCancellation_releasesOnlyCancelledDates_keepsRestBlocked() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);
        JsonNode booking = createBooking(shareToken, itemId, "2026-10-11", "2026-10-19");
        String reference = booking.path("bookingReference").asText();

        mockMvc.perform(post("/api/admin/rental-bookings/" + reference + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/rental-bookings/" + reference + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"startDate\": \"2026-10-11\", \"endDate\": \"2026-10-13\" }"))
                .andExpect(status().isOk())
                // Booking as a whole is still active -- only part of it was cancelled.
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        String unavailableResponse = mockMvc.perform(
                        get("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/unavailable-dates"))
                .andReturn().getResponse().getContentAsString();
        JsonNode dates = objectMapper.readTree(unavailableResponse).path("data").path("unavailableDates");
        assertThat(dates).hasSize(6);
        assertThat(dates.toString()).doesNotContain("2026-10-11").doesNotContain("2026-10-12").doesNotContain("2026-10-13");
        assertThat(dates.toString()).contains("2026-10-14").contains("2026-10-19");

        // Freed sub-range (11-13) can now be booked by someone else.
        mockMvc.perform(post("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload("2026-10-11", "2026-10-13")))
                .andExpect(status().isCreated());

        // But the still-blocked remainder (14-19) cannot.
        mockMvc.perform(post("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload("2026-10-15", "2026-10-16")))
                .andExpect(status().isConflict());
    }

    @Test
    void historicalBookingPrice_unaffectedByLaterPriceChange() throws Exception {

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);

        JsonNode booking = createBooking(shareToken, itemId, IN_SEASON_DAY, IN_SEASON_DAY);
        assertThat(booking.path("dailyRate").asInt()).isEqualTo(800);

        mockMvc.perform(put("/api/admin/rental-catalogs/" + adminCatalogIdFor(shareToken) + "/items/" + itemId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Blue Lehenga\", \"colour\": \"Blue\", \"rentalPrice\": 1000 }"))
                .andExpect(status().isOk());

        String reference = booking.path("bookingReference").asText();
        mockMvc.perform(get("/api/admin/rental-bookings/" + reference)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.dailyRate").value(800))
                .andExpect(jsonPath("$.data.totalAmount").value(800));
    }

    private Long adminCatalogIdFor(String shareToken) throws Exception {

        String response = mockMvc.perform(get("/api/admin/rental-catalogs")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();

        JsonNode catalogs = objectMapper.readTree(response).path("data");
        for (JsonNode c : catalogs) {
            if (c.path("shareToken").asText().equals(shareToken)) {
                return c.path("id").asLong();
            }
        }
        throw new IllegalStateException("Catalog not found for share token");
    }

    @Test
    void adminBookingEndpoints_rejectNonAdminCustomer() throws Exception {

        mockMvc.perform(get("/api/admin/rental-bookings")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminBookingEndpoints_rejectUnauthenticatedRequest() throws Exception {

        mockMvc.perform(get("/api/admin/rental-bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownShareToken_bookingCreation_returnsNotFound() throws Exception {

        mockMvc.perform(post("/api/rental-catalogs/this-token-does-not-exist/items/1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(IN_SEASON_DAY, IN_SEASON_DAY)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminSettings_canBeUpdated_andPublicSettingsReflectChange() throws Exception {

        String shareToken = createCatalogAndItem(800);

        String updatePayload = """
                {
                  "seasonStartDate": "2026-10-11",
                  "seasonEndDate": "2026-10-19",
                  "whatsappNumber": "9999999999",
                  "pickupInstructions": "Pickup 5-8 PM",
                  "returnInstructions": "Return by 1 PM",
                  "lateReturnMessage": "Late fee applies",
                  "paymentInstructions": "Pay and send screenshot",
                  "termsAndConditions": "Test terms",
                  "pendingPaymentExpiryMinutes": 45
                }
                """;

        mockMvc.perform(put("/api/admin/rental-settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.whatsappNumber").value("9999999999"));

        mockMvc.perform(get("/api/rental-catalogs/" + shareToken + "/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.whatsappNumber").value("9999999999"))
                .andExpect(jsonPath("$.data.pickupInstructions").value("Pickup 5-8 PM"));
    }

    @Test
    void expiryScheduler_cancelsAbandonedPendingBooking_andReleasesDates() throws Exception {

        // 1-minute expiry (the configured floor); the booking below is
        // then backdated to simulate having sat unpaid for an hour --
        // faster and more deterministic than actually sleeping in a test.
        String updatePayload = """
                {
                  "seasonStartDate": "2026-10-11",
                  "seasonEndDate": "2026-10-19",
                  "whatsappNumber": "6269933231",
                  "pickupInstructions": "x",
                  "returnInstructions": "x",
                  "lateReturnMessage": "x",
                  "paymentInstructions": "x",
                  "termsAndConditions": "x",
                  "pendingPaymentExpiryMinutes": 1
                }
                """;
        mockMvc.perform(put("/api/admin/rental-settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk());

        String shareToken = createCatalogAndItem(800);
        Long itemId = firstItemId(shareToken);

        JsonNode booking = createBooking(shareToken, itemId, IN_SEASON_DAY, IN_SEASON_DAY);
        String reference = booking.path("bookingReference").asText();

        // createdAt is @CreationTimestamp + updatable=false (INSERT-only by
        // design, see RentalBooking's javadoc) -- a normal save() can never
        // change it, so a native update is the only way to simulate "this
        // booking has sat unpaid for an hour" in a test.
        Long bookingId = rentalBookingRepository.findByBookingReference(reference).orElseThrow().getId();
        entityManager.createNativeQuery("UPDATE rental_bookings SET created_at = ? WHERE id = ?")
                .setParameter(1, java.time.LocalDateTime.now().minusHours(1))
                .setParameter(2, bookingId)
                .executeUpdate();
        entityManager.clear();

        rentalBookingService.expireAbandonedPendingBookings();

        mockMvc.perform(get("/api/rental-catalogs/" + shareToken + "/items/" + itemId + "/unavailable-dates"))
                .andExpect(jsonPath("$.data.unavailableDates.length()").value(0));
    }
}
