package com.stylenest.stylenest_backend.security;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.stylenest.stylenest_backend.entity.BulkAccessToken;
import com.stylenest.stylenest_backend.service.BulkTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * The real access-control gate for the private wholesale catalog. Runs on
 * every /api/bulk/customer/** request (registered in WebMvcConfig) and
 * re-validates the X-Bulk-Token header against the database every single
 * time -- these endpoints are "permitAll" in SecurityConfig at the Spring
 * Security layer purely because bulk customers are never authenticated
 * Users/JWT holders, but that does NOT mean unauthenticated access: this
 * interceptor is what actually enforces it, so hiding the frontend link is
 * never the only protection.
 *
 * Exceptions thrown here (BulkTokenInvalidException / BulkTokenRevokedException)
 * propagate to GlobalExceptionHandler exactly like a controller-thrown one,
 * since Spring MVC routes preHandle exceptions through the same
 * HandlerExceptionResolver chain.
 */
@Component
@RequiredArgsConstructor
public class BulkAccessInterceptor implements HandlerInterceptor {

    public static final String BULK_TOKEN_HEADER = "X-Bulk-Token";
    public static final String BULK_TOKEN_REQUEST_ATTRIBUTE = "bulkAccessToken";

    private final BulkTokenService bulkTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        String headerToken = request.getHeader(BULK_TOKEN_HEADER);

        BulkAccessToken token = bulkTokenService.resolveActiveToken(headerToken);

        request.setAttribute(BULK_TOKEN_REQUEST_ATTRIBUTE, token);

        return true;
    }
}
