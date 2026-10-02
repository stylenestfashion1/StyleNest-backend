package com.stylenest.stylenest_backend.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.exception.DtdcApiException;
import com.stylenest.stylenest_backend.service.courier.CourierTrackingService;

/**
 * The only class in this codebase that talks to the DTDC courier API
 * directly -- everything else (ShipmentServiceImpl) goes through the
 * CourierTrackingService interface. Mirrors CashfreePaymentProviderClient's
 * shape: plain REST via Spring's RestClient, lazily built so a StyleNest
 * environment with no DTDC credentials yet can still start up cleanly.
 *
 * Request/response shapes, endpoints and header names here are exactly
 * what DTDC's own PDFs (Order Upload/Booking API RFT v2.0, Shipping Label
 * API WS v2.0, Cancellation API v2.0, REST Tracking API v4 JSON) document
 * -- nothing guessed from general courier-API knowledge. Two DOCUMENTED
 * gaps this class has to work around defensively rather than guess around
 * (both called out in the DTDC integration final report, not silently
 * assumed):
 *  - The tracking "authenticate" endpoint's doc never shows a sample
 *    response body/field name for the token -- tokenFor() below accepts
 *    either a bare token string or a JSON object with a token/access_token
 *    field, whichever DTDC actually sends.
 *  - A failed consignment's error-reason field name isn't shown in the
 *    Order Upload API doc (only prose: "the response contains an error
 *    message reason") -- extractFailureReason() below checks several
 *    plausible field names DTDC commonly uses in this family of APIs.
 */
@Service
public class DtdcShippingProviderClient implements CourierTrackingService {

    private static final String PROD_BASE_URL = "https://pxapi.dtdc.in";
    private static final String STAGING_BASE_URL = "https://alphademodashboardapi.shipsy.io";
    private static final String PROD_TRACKING_BASE_URL = "https://blktracksvc.dtdc.com";
    private static final String STAGING_TRACKING_BASE_URL = "https://dtdcstagingapi.dtdc.com";

    private static final Logger log = LoggerFactory.getLogger(DtdcShippingProviderClient.class);

    // The shipping-label variant a warehouse actually prints and sticks on
    // a parcel -- the other label_code options (A4/A6/POD/route/address/
    // invoice) exist in the doc but aren't needed for this admin flow.
    private static final String LABEL_CODE = "SHIP_LABEL_4X6";

    private final String apiKey;
    private final String customerCode;
    private final String serviceTypeId;
    private final String commodityId;
    private final String trackingUsername;
    private final String trackingPassword;
    private final String originName;
    private final String originPhone;
    private final String originState;
    private final String originAddressLine1;
    private final String originCity;
    private final String originPincode;
    private final String baseUrl;
    private final String trackingBaseUrl;

    private RestClient restClient;
    private RestClient trackingRestClient;
    private volatile String cachedTrackingToken;

    public DtdcShippingProviderClient(
            @Value("${dtdc.api-key:}") String apiKey,
            @Value("${dtdc.customer-code:}") String customerCode,
            @Value("${dtdc.service-type-id:B2C PRIORITY}") String serviceTypeId,
            @Value("${dtdc.commodity-id:Apparel}") String commodityId,
            @Value("${dtdc.tracking-username:}") String trackingUsername,
            @Value("${dtdc.tracking-password:}") String trackingPassword,
            @Value("${dtdc.mode:production}") String mode,
            @Value("${app.invoice.seller.name:}") String originName,
            @Value("${app.invoice.seller.phone:}") String originPhone,
            @Value("${app.invoice.seller.state:}") String originState,
            @Value("${dtdc.origin.address-line1:}") String originAddressLine1,
            @Value("${dtdc.origin.city:}") String originCity,
            @Value("${dtdc.origin.pincode:}") String originPincode) {

        this.apiKey = apiKey;
        this.customerCode = customerCode;
        this.serviceTypeId = serviceTypeId;
        this.commodityId = commodityId;
        this.trackingUsername = trackingUsername;
        this.trackingPassword = trackingPassword;
        this.originName = originName;
        this.originPhone = originPhone;
        this.originState = originState;
        this.originAddressLine1 = originAddressLine1;
        this.originCity = originCity;
        this.originPincode = originPincode;

        boolean staging = "staging".equalsIgnoreCase(mode);
        this.baseUrl = staging ? STAGING_BASE_URL : PROD_BASE_URL;
        this.trackingBaseUrl = staging ? STAGING_TRACKING_BASE_URL : PROD_TRACKING_BASE_URL;
    }

    private synchronized RestClient client() {

        if (apiKey.isBlank() || customerCode.isBlank()) {
            throw new DtdcApiException(
                    "DTDC courier integration is not configured yet. Please set DTDC_API_KEY / DTDC_CUSTOMER_CODE.");
        }

        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("api-key", apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }

        return restClient;
    }

    private synchronized RestClient trackingClient() {

        if (trackingRestClient == null) {
            trackingRestClient = RestClient.builder()
                    .baseUrl(trackingBaseUrl)
                    .build();
        }

        return trackingRestClient;
    }

    @Override
    public BookingResult bookShipment(ShipmentBookingRequest request) {

        Map<String, Object> body = buildBookingBody(request);

        try {

            ResponseEntity<JsonNode> responseEntity = client().post()
                    .uri("/api/customer/integration/consignment/softdata")
                    .body(body)
                    .retrieve()
                    .toEntity(JsonNode.class);

            int httpStatus = responseEntity.getStatusCode().value();
            JsonNode response = responseEntity.getBody();

            if (response == null) {
                log.warn("DTDC booking response empty: httpStatus={}, orderReference={}",
                        httpStatus, request.customerReferenceNumber());
                throw new DtdcApiException("DTDC returned an empty response.");
            }

            JsonNode first = response.path("data").path(0);

            if (!first.path("success").asBoolean(false)) {
                log.warn("DTDC booking rejected: httpStatus={}, orderReference={}, body={}",
                        httpStatus, request.customerReferenceNumber(), response);
                throw new DtdcApiException("DTDC could not book this shipment: " + extractFailureReason(first));
            }

            log.info("DTDC booking accepted: httpStatus={}, orderReference={}, body={}",
                    httpStatus, request.customerReferenceNumber(), response);

            String referenceNumber = first.path("reference_number").asText(null);

            if (referenceNumber == null || referenceNumber.isBlank()) {
                throw new DtdcApiException("DTDC did not return a reference number for this shipment.");
            }

            return new BookingResult(referenceNumber);

        } catch (DtdcApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("DTDC booking HTTP error: httpStatus={}, orderReference={}, responseBody={}",
                    e.getStatusCode().value(), request.customerReferenceNumber(), e.getResponseBodyAsString());
            throw new DtdcApiException("Could not reach DTDC to book this shipment. Please try again.");
        } catch (RestClientException e) {
            log.warn("DTDC booking communication failure: orderReference={}, error={}",
                    request.customerReferenceNumber(), e.getMessage());
            throw new DtdcApiException("Could not reach DTDC to book this shipment. Please try again.");
        }
    }

    Map<String, Object> buildBookingBody(ShipmentBookingRequest request) {

        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("name", originName);
        origin.put("phone", originPhone);
        origin.put("alternate_phone", "");
        origin.put("address_line_1", originAddressLine1);
        origin.put("pincode", originPincode);
        origin.put("city", originCity);
        origin.put("state", originState);

        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("name", request.consigneeName());
        destination.put("phone", request.consigneePhone());
        destination.put("alternate_phone", "");
        destination.put("address_line_1", request.consigneeAddressLine1());
        destination.put("address_line_2", request.consigneeAddressLine2() == null ? "" : request.consigneeAddressLine2());
        destination.put("pincode", request.consigneePincode());
        destination.put("city", request.consigneeCity());
        destination.put("state", request.consigneeState());

        Map<String, Object> consignment = new LinkedHashMap<>();
        consignment.put("customer_code", customerCode);
        consignment.put("service_type_id", serviceTypeId);
        consignment.put("load_type", "NON-DOCUMENT");
        consignment.put("description", "");
        consignment.put("consignment_type", "Forward");
        consignment.put("dimension_unit", "CM");
        consignment.put("length", request.lengthCm().toPlainString());
        consignment.put("width", request.widthCm().toPlainString());
        consignment.put("height", request.heightCm().toPlainString());
        consignment.put("weight_unit", "KG");
        consignment.put("weight", request.weightKg().toPlainString());
        consignment.put("declared_value", request.declaredValue().toPlainString());
        consignment.put("num_pieces", String.valueOf(request.numPieces()));
        consignment.put("origin_details", origin);
        consignment.put("destination_details", destination);
        consignment.put("customer_reference_number", request.customerReferenceNumber());
        consignment.put("cod_collection_mode", request.cashOnDelivery() ? "CASH" : "");
        consignment.put("cod_amount",
                request.cashOnDelivery() && request.codAmount() != null ? request.codAmount().toPlainString() : "");
        consignment.put("cod_favor_of", "");
        consignment.put("commodity_id", commodityId);
        consignment.put("is_risk_surcharge_applicable", false);

        return Map.of("consignments", List.of(consignment));
    }

    @Override
    public void cancelShipment(String trackingNumber) {

        Map<String, Object> body = Map.of(
                "AWBNo", List.of(trackingNumber),
                "customerCode", customerCode);

        try {

            JsonNode response = client().post()
                    .uri("/api/customer/integration/consignment/cancel")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (!response.path("success").asBoolean(false)) {
                throw new DtdcApiException("DTDC could not cancel this shipment.");
            }

        } catch (RestClientException e) {

            throw new DtdcApiException("Could not reach DTDC to cancel this shipment. Please try again.");
        }
    }

    @Override
    public byte[] fetchLabel(String trackingNumber) {

        try {

            byte[] pdf = client().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/customer/integration/consignment/shippinglabel/stream")
                            .queryParam("reference_number", trackingNumber)
                            .queryParam("label_code", LABEL_CODE)
                            .queryParam("label_format", "pdf")
                            .build())
                    .retrieve()
                    .body(byte[].class);

            if (pdf == null || pdf.length == 0) {
                throw new DtdcApiException("DTDC did not return a label for this shipment.");
            }

            return pdf;

        } catch (RestClientException e) {

            throw new DtdcApiException("Could not reach DTDC to fetch the shipping label. Please try again.");
        }
    }

    @Override
    public Optional<CourierTrackingSnapshot> fetchTracking(String courierName, String trackingNumber) {

        if (!"DTDC".equalsIgnoreCase(courierName)) {
            return Optional.empty();
        }

        if (trackingUsername.isBlank() || trackingPassword.isBlank()) {
            throw new DtdcApiException(
                    "DTDC tracking is not configured yet. Please set DTDC_TRACKING_USERNAME / DTDC_TRACKING_PASSWORD.");
        }

        return Optional.of(parseTrackingSnapshot(trackWithRetry(trackingNumber, true)));
    }

    private JsonNode trackWithRetry(String trackingNumber, boolean allowRetry) {

        try {

            JsonNode response = trackingClient().post()
                    .uri("/dtdc-tracking-api/dtdc-api/rest/JSONCnTrk/getTrackDetails")
                    .header("Content-Type", "application/json")
                    .header("x-access-token", tokenFor())
                    .body(Map.of("trkType", "cnno", "strcnno", trackingNumber, "addtnlDtl", "Y"))
                    .retrieve()
                    .body(JsonNode.class);

            if (!response.path("statusFlag").asBoolean(false)) {
                throw new DtdcApiException("DTDC could not find tracking details for this shipment.");
            }

            return response;

        } catch (RestClientResponseException e) {

            if (allowRetry && e.getStatusCode().value() == 401) {
                // Per the doc, a tracking token "never expires but it can
                // be revoked" -- if that happens, drop the cached one and
                // authenticate exactly once more before giving up.
                cachedTrackingToken = null;
                return trackWithRetry(trackingNumber, false);
            }

            throw new DtdcApiException("Could not reach DTDC to fetch tracking details. Please try again.");

        } catch (RestClientException e) {

            throw new DtdcApiException("Could not reach DTDC to fetch tracking details. Please try again.");
        }
    }

    private synchronized String tokenFor() {

        if (cachedTrackingToken != null) {
            return cachedTrackingToken;
        }

        try {

            String rawResponse = trackingClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dtdc-api/api/dtdc/authenticate")
                            .queryParam("username", trackingUsername)
                            .queryParam("password", trackingPassword)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new DtdcApiException("DTDC did not return a tracking token.");
            }

            cachedTrackingToken = extractToken(rawResponse.trim());

            return cachedTrackingToken;

        } catch (RestClientException e) {

            throw new DtdcApiException("Could not authenticate with DTDC's tracking service. Please try again.");
        }
    }

    // See class javadoc: the doc never shows this endpoint's actual
    // response shape, so both a bare token string and a JSON-wrapped one
    // are accepted rather than assuming either.
    private String extractToken(String rawResponse) {

        if (!rawResponse.startsWith("{")) {
            return rawResponse.replace("\"", "");
        }

        try {

            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawResponse);
            String token = firstNonBlank(
                    node.path("token").asText(null),
                    node.path("access_token").asText(null),
                    node.path("data").asText(null));

            if (token == null) {
                throw new DtdcApiException("DTDC returned an unrecognized authentication response.");
            }

            return token;

        } catch (DtdcApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DtdcApiException("DTDC returned an unrecognized authentication response.");
        }
    }

    private CourierTrackingSnapshot parseTrackingSnapshot(JsonNode response) {

        JsonNode header = response.path("trackHeader");
        JsonNode details = response.path("trackDetails");

        JsonNode latestEvent = (details.isArray() && !details.isEmpty())
                ? details.get(details.size() - 1)
                : null;

        ShipmentStatus mapped = mapDtdcStatus(
                header.path("strStatus").asText(""),
                latestEvent == null ? "" : latestEvent.path("strAction").asText(""));

        String location = latestEvent == null
                ? header.path("strDestination").asText(null)
                : firstNonBlank(
                        latestEvent.path("strDestination").asText(null),
                        latestEvent.path("strOrigin").asText(null));

        String description = latestEvent == null ? null : latestEvent.path("strAction").asText(null);

        LocalDateTime eventTimestamp = latestEvent == null ? null : parseDtdcDateTime(
                latestEvent.path("strActionDate").asText(null),
                latestEvent.path("strActionTime").asText(null));

        return new CourierTrackingSnapshot(mapped, location, eventTimestamp, description);
    }

    /**
     * DTDC's tracking status vocabulary (trackHeader.strStatus: DELIVERED /
     * DELIVERY PROCESS IN PROGRESS / ATTEMPTED / HELDUP / RTO, per the REST
     * Tracking API v4 doc) mapped onto StyleNest's existing ShipmentStatus.
     * Two of these have no exact StyleNest equivalent and are a documented
     * judgment call, not a 1:1 mapping:
     *  - ATTEMPTED (a delivery attempt failed, DTDC will retry) -> IN_TRANSIT
     *  - HELDUP (delayed within DTDC's network) -> IN_TRANSIT
     * Both mean "still with the courier, not delivered, not returned,"
     * which IN_TRANSIT already captures; StyleNest has no separate
     * "delivery attempt failed" or "held up" status.
     */
    ShipmentStatus mapDtdcStatus(String overallStatus, String latestAction) {

        String status = overallStatus == null ? "" : overallStatus.trim().toUpperCase();
        String action = latestAction == null ? "" : latestAction.trim().toUpperCase();

        if (status.equals("DELIVERED")) {
            return ShipmentStatus.DELIVERED;
        }

        if (status.equals("RTO")) {
            return ShipmentStatus.RETURNED;
        }

        if (status.equals("DELIVERY PROCESS IN PROGRESS")) {
            return action.contains("OUT FOR DELIVERY") ? ShipmentStatus.OUT_FOR_DELIVERY : ShipmentStatus.IN_TRANSIT;
        }

        if (status.equals("ATTEMPTED") || status.equals("HELDUP")) {
            return ShipmentStatus.IN_TRANSIT;
        }

        if (action.contains("PICKED UP")) {
            return ShipmentStatus.SHIPPED;
        }

        // Booked / pickup awaited / pickup scheduled / pickup reassigned /
        // not picked -- DTDC has the consignment but hasn't physically
        // collected it from the origin yet.
        return ShipmentStatus.PACKED;
    }

    private LocalDateTime parseDtdcDateTime(String ddmmyyyy, String hhmm) {

        if (ddmmyyyy == null || ddmmyyyy.isBlank()) {
            return null;
        }

        try {

            String time = (hhmm == null || hhmm.isBlank()) ? "0000" : hhmm;

            return LocalDateTime.parse(
                    ddmmyyyy + time,
                    DateTimeFormatter.ofPattern("ddMMyyyyHHmm"));

        } catch (Exception e) {
            return null;
        }
    }

    // Tries several plausible field names for a failed consignment's error
    // message -- the Order Upload API doc never states the exact field
    // name (see class javadoc), only that "the response contains an error
    // message reason".
    private String extractFailureReason(JsonNode node) {

        String reason = firstNonBlank(
                node.path("reason").asText(null),
                node.path("remark").asText(null),
                node.path("remarks").asText(null),
                node.path("message").asText(null),
                node.path("error").asText(null));

        return reason == null ? "no reason given." : reason;
    }

    private String firstNonBlank(String... values) {

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }
}
