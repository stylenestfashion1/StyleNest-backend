package com.stylenest.stylenest_backend.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // Comma-separated. Frontend domain isn't finalized yet -- default
    // keeps local dev working; the real production origin gets added via
    // this env var only, no code change/redeploy needed.
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))

                .authorizeHttpRequests(auth -> auth

                        // OPTIONS (React/Vercel)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // HOME
                        .requestMatchers("/").permitAll()

                        // UPLOADED PRODUCT IMAGES (publicly viewable, same
                        // as any other product image URL -- uploading
                        // itself is admin-only via /api/admin/images/upload,
                        // covered by the /api/admin/** rule below)
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()

                        // SWAGGER
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/webjars/**"
                        ).permitAll()

                        // AUTH
                        .requestMatchers("/api/auth/**").permitAll()

                        // PAYMENT GATEWAY WEBHOOK (Cashfree posts here
                        // server-to-server with no StyleNest JWT --
                        // authenticated by its own HMAC signature instead,
                        // verified in PaymentServiceImpl.handleWebhook)
                        .requestMatchers(HttpMethod.POST,
                                "/api/payments/webhook").permitAll()

                        // PAYMENT VERIFY (called by our frontend right after
                        // the Cashfree Checkout call resolves, for both
                        // registered and guest checkout -- the internal
                        // order is resolved from our own stored
                        // providerOrderId, never from a client-supplied user
                        // context, and the real outcome is always
                        // independently re-confirmed server-to-server with
                        // Cashfree in PaymentServiceImpl.verifyPayment)
                        .requestMatchers(HttpMethod.POST,
                                "/api/payments/verify").permitAll()

                        // GUEST CHECKOUT (no login/registration/OTP -- access
                        // control for tracking/invoice is order-number +
                        // phone, verified in GuestOrderServiceImpl)
                        .requestMatchers(HttpMethod.POST,
                                "/api/guest/orders",
                                "/api/guest/orders/track",
                                "/api/payments/guest/initiate"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET,
                                "/api/guest/orders/invoice",
                                "/api/guest/orders/invoice/view").permitAll()

                        // POSTAL/ZIP CODE LOOKUP (read-only, no PII -- needed
                        // pre-auth so guest checkout can use it too)
                        .requestMatchers(HttpMethod.GET,
                                "/api/postal-lookup").permitAll()

                        // IN-STORE QR DISCOUNT -- CUSTOMER FLOW. permitAll here
                        // only because a shop customer scanning the QR is never
                        // an authenticated User/JWT holder -- the real
                        // enforcement is inside DiscountOfferService: /verify
                        // requires the exact permanent QR secret token, and
                        // /claim requires a valid short-lived session token
                        // minted by /verify (see DiscountOfferServiceImpl).
                        // Admin discount endpoints live under /api/admin/discount/**,
                        // already covered by the hasRole("ADMIN") /api/admin/** rule below.
                        .requestMatchers(HttpMethod.POST,
                                "/api/discount/verify",
                                "/api/discount/claim").permitAll()

                        // NAVRATRI RENTAL CATALOG -- PUBLIC CUSTOMER VIEW.
                        // permitAll here only because a WhatsApp-link visitor
                        // is never an authenticated User/JWT holder -- real
                        // access control is the share token itself
                        // (unguessable, SecureRandom-generated; see
                        // RentalCatalogServiceImpl). Admin rental endpoints
                        // live under /api/admin/rental-catalogs/**, already
                        // covered by the hasRole("ADMIN") /api/admin/** rule
                        // below. Temporary feature -- safe to remove this
                        // line plus the whole rental package after Navratri.
                        .requestMatchers(HttpMethod.GET,
                                "/api/rental-catalogs/*").permitAll()

                        // NAVRATRI RENTAL BOOKING -- PUBLIC CUSTOMER FLOW.
                        // Same trust boundary as the catalog view above: no
                        // StyleNest customer account is ever involved (see
                        // spec section 8/24), so this permitAll is only at
                        // the Spring Security layer. Real access control is
                        // still the share token, re-checked on every one of
                        // these calls inside RentalBookingServiceImpl /
                        // RentalSettingsServiceImpl. Admin booking
                        // management lives under /api/admin/rental-bookings/**
                        // and /api/admin/rental-settings/**, already covered
                        // by the hasRole("ADMIN") /api/admin/** rule below.
                        .requestMatchers(HttpMethod.GET,
                                "/api/rental-catalogs/*/settings",
                                "/api/rental-catalogs/*/items/*/unavailable-dates").permitAll()

                        .requestMatchers(HttpMethod.POST,
                                "/api/rental-catalogs/*/items/*/bookings",
                                "/api/rental-catalogs/*/bookings/*/payment").permitAll()

                        // BULK ORDERS -- ACCESS GATE (public, rate-limited
                        // inside BulkTokenServiceImpl)
                        .requestMatchers(HttpMethod.POST,
                                "/api/bulk/access/validate").permitAll()

                        // BULK ORDERS -- CUSTOMER CATALOG/CHECKOUT. permitAll
                        // here at the Spring Security layer only because
                        // bulk customers are never authenticated Users/JWT
                        // holders -- real enforcement is BulkAccessInterceptor
                        // (see WebMvcConfig), which re-validates the
                        // X-Bulk-Token header against the database on every
                        // single request to this path, independent of the
                        // frontend ever hiding the link.
                        .requestMatchers("/api/bulk/customer/**").permitAll()

                        // PRODUCTS (PUBLIC)
                        .requestMatchers(HttpMethod.GET,
                                "/api/products/**").permitAll()

                        .requestMatchers(HttpMethod.POST,
                                "/api/products/search").permitAll()

                        // PRODUCTS (ADMIN)                       
                        .requestMatchers(HttpMethod.POST,
                                "/api/products").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.PUT,
                                "/api/products/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.DELETE,
                                "/api/products/**").hasRole("ADMIN")

                        // SCROLL-DOWN IMAGES (PUBLIC READ; admin write/delete
                        // is covered by the /api/admin/** rule below)
                        .requestMatchers(HttpMethod.GET,
                                "/api/scroll-images").permitAll()

                        // CATEGORIES (PUBLIC)
                        .requestMatchers(HttpMethod.GET,
                                "/api/categories/**").permitAll()

                        // CATEGORIES (ADMIN)
                        .requestMatchers(HttpMethod.POST,
                                "/api/categories").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.PUT,
                                "/api/categories/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.DELETE,
                                "/api/categories/**").hasRole("ADMIN")

                        // PRODUCT VARIANTS
                        .requestMatchers(HttpMethod.GET,
                                "/api/products/*/variants").permitAll()

                        .requestMatchers(HttpMethod.POST,
                                "/api/products/*/variants").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.PUT,
                                "/api/variants/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.DELETE,
                                "/api/variants/**").hasRole("ADMIN")

                        // PRODUCT IMAGES
                        .requestMatchers(HttpMethod.GET,
                                "/api/variants/*/images").permitAll()

                        .requestMatchers(HttpMethod.POST,
                                "/api/variants/*/images").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.DELETE,
                                "/api/images/**").hasRole("ADMIN")

                        // CUSTOMER APIs
                        .requestMatchers("/api/cart/**").authenticated()

                        .requestMatchers("/api/wishlist/**").authenticated()

                        .requestMatchers("/api/address/**").authenticated()

                        .requestMatchers("/api/orders/**").authenticated()

                        .requestMatchers("/api/checkout/**").authenticated()

                        // ADMIN APIs
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // REMAINING APIs
                        .requestMatchers("/api/**").authenticated()

                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {

        return config.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .toList());

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "PATCH",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of("*"));

        configuration.setExposedHeaders(List.of("Authorization"));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}