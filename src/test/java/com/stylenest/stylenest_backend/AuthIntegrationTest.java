package com.stylenest.stylenest_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Role;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.security.CustomUserDetailsService;
import com.stylenest.stylenest_backend.security.JwtService;

/**
 * Full-stack coverage for the 7-day customer session + strong-password
 * requirements: registration/reset password validation, real JWT
 * issuance/expiration, protected endpoint access, and that a password never
 * appears in any response body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    void register_withPasswordTooShort_isRejected() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test User","email":"short@test.com","password":"Ab@1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(userRepository.existsByEmail("short@test.com")).isFalse();
    }

    @Test
    void register_withNoUppercase_isRejected() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test User","email":"noupper@test.com","password":"abcd@123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withNoLowercase_isRejected() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test User","email":"nolower@test.com","password":"ABCD@123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withNoNumber_isRejected() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test User","email":"nonum@test.com","password":"Abcd@efgh"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withNoSpecialCharacter_isRejected() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test User","email":"nospecial@test.com","password":"Abcdefgh1"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withValidPassword_isAccepted_andPasswordNeverAppearsInResponse() throws Exception {

        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test User","email":"validpw@test.com","password":"Abcd@123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("Abcd@123");
        assertThat(userRepository.existsByEmail("validpw@test.com")).isTrue();
    }

    @Test
    void login_succeedsAndIssuesA7DayTokenForCustomer_andNeverReturnsPassword() throws Exception {

        User customer = User.builder()
                .fullName("Login Customer")
                .email("logincust@test.com")
                .password(passwordEncoder.encode("Abcd@123"))
                .phone("9999999999")
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(customer);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"logincust@test.com","password":"Abcd@123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("Abcd@123");

        String token = com.jayway.jsonpath.JsonPath.read(body, "$.token");

        var claims = io.jsonwebtoken.Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-only-jwt-signing-secret-not-used-in-production-0123456789".getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        long durationMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        assertThat(durationMs).isCloseTo(604_800_000L, org.assertj.core.data.Offset.offset(5_000L));
    }

    @Test
    void validCustomerToken_grantsAccessToProtectedEndpoint() throws Exception {

        User customer = User.builder()
                .fullName("Protected Access Customer")
                .email("protectedaccess@test.com")
                .password(passwordEncoder.encode("Abcd@123"))
                .phone("9999999999")
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(customer);

        UserDetails userDetails = userDetailsService.loadUserByUsername(customer.getEmail());
        String token = jwtService.generateToken(userDetails, Role.CUSTOMER);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("protectedaccess@test.com"));
    }

    @Test
    void expiredCustomerToken_isRejectedWith401_notARawException() throws Exception {

        User customer = User.builder()
                .fullName("Expired Token Customer")
                .email("expiredtoken@test.com")
                .password(passwordEncoder.encode("Abcd@123"))
                .phone("9999999999")
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(customer);

        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .subject(customer.getEmail())
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 20_000))
                .expiration(new java.util.Date(System.currentTimeMillis() - 10_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-only-jwt-signing-secret-not-used-in-production-0123456789".getBytes()))
                .compact();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required. Please log in again."));
    }

    @Test
    void adminLoginAndAuthorization_stillWorkAfterCustomerSessionChange() throws Exception {

        User admin = User.builder()
                .fullName("Test Admin")
                .email("stilladmin@test.com")
                .password(passwordEncoder.encode("Abcd@123"))
                .phone("9999999997")
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"stilladmin@test.com","password":"Abcd@123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(body, "$.token");

        var claims = io.jsonwebtoken.Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-only-jwt-signing-secret-not-used-in-production-0123456789".getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        long durationMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        // Admin keeps the pre-existing (short) test-profile duration of 1
        // hour -- must NOT have picked up the customer's 7-day duration.
        assertThat(durationMs).isCloseTo(3_600_000L, org.assertj.core.data.Offset.offset(5_000L));
    }

    @Test
    void resetPassword_withWeakNewPassword_isRejected() throws Exception {

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"someone@test.com","newPassword":"weak"}"""))
                .andExpect(status().isBadRequest());
    }
}
