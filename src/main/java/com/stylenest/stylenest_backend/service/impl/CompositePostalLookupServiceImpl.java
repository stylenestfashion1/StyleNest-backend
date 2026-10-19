package com.stylenest.stylenest_backend.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.stylenest.stylenest_backend.dto.postal.PostalLookupResponse;
import com.stylenest.stylenest_backend.service.PostalLookupService;

/**
 * Composite postal-code lookup implementation routing to whichever
 * provider gives usable city-level data for a given country -- there is no
 * single free source with good coverage everywhere:
 *
 *  - India (IN): India Post's official pincode API (api.postalpincode.in).
 *  - United Kingdom (GB): postcodes.io (supports both full postcodes like
 *    SW1A 1AA and outcodes/area codes like SW1A, EC1, W1).
 *  - Everyone else (US, AU, CA, ...): api.zippopotam.us.
 */
@Service
public class CompositePostalLookupServiceImpl implements PostalLookupService {

    private static final Set<String> AU_CAPITALS = Set.of(
            "sydney", "melbourne", "brisbane", "perth",
            "adelaide", "canberra", "hobart", "darwin");

    private final RestClient restClient;

    public CompositePostalLookupServiceImpl(RestClient postalLookupRestClient) {
        this.restClient = postalLookupRestClient;
    }

    @Value("${postal-lookup.base-url}")
    private String zippopotamBaseUrl;

    @Value("${postal-lookup.india-base-url}")
    private String indiaBaseUrl;

    @Value("${postal-lookup.uk-base-url}")
    private String ukBaseUrl;

    @Override
    public PostalLookupResponse lookup(String countryCode, String postalCode) {

        if (isBlank(countryCode) || isBlank(postalCode)) {
            return PostalLookupResponse.notFound();
        }

        String cc = countryCode.trim().toUpperCase();
        String code = postalCode.trim();

        try {

            return switch (cc) {
                case "IN" -> lookupIndia(code);
                case "GB" -> lookupUk(code);
                default -> lookupZippopotam(cc.toLowerCase(), code);
            };

        } catch (Exception ex) {

            // ANY failure here -- 404, unsupported country, timeout,
            // malformed response -- must never block checkout.
            return PostalLookupResponse.notFound();
        }
    }

    @SuppressWarnings("unchecked")
    private PostalLookupResponse lookupIndia(String postalCode) {

        List<Object> body = restClient.get()
                .uri(indiaBaseUrl + "/pincode/{code}", postalCode)
                .header("User-Agent", "StyleNest-Backend/1.0")
                .retrieve()
                .body(List.class);

        if (body == null || body.isEmpty()) {
            return PostalLookupResponse.notFound();
        }

        Map<String, Object> first = (Map<String, Object>) body.get(0);

        if (!"Success".equals(first.get("Status"))) {
            return PostalLookupResponse.notFound();
        }

        Object postOfficesRaw = first.get("PostOffice");

        if (!(postOfficesRaw instanceof List<?> postOffices) || postOffices.isEmpty()) {
            return PostalLookupResponse.notFound();
        }

        Map<String, Object> office = (Map<String, Object>) postOffices.get(0);

        return PostalLookupResponse.builder()
                .found(true)
                .city(stringOrNull(office.get("District")))
                .state(stringOrNull(office.get("State")))
                .country(stringOrNull(office.get("Country")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private PostalLookupResponse lookupUk(String postalCode) {

        String clean = postalCode.trim();
        String normalized = clean.replaceAll("\\s+", "");

        // UK full postcodes end with [digit][letter][letter] (e.g. 1AA, 4NP) and are at least 5 chars.
        // Outward codes (e.g. SW1A, W1, EC1, M1) are 2-4 chars and represent districts/towns.
        boolean isOutcode = normalized.length() <= 4 || !normalized.matches(".*\\d[A-Za-z]{2}$");

        if (isOutcode) {
            Map<String, Object> body;
            try {
                body = restClient.get()
                        .uri(ukBaseUrl + "/outcodes/{code}", normalized)
                        .retrieve()
                        .body(Map.class);
            } catch (Exception ex) {
                return PostalLookupResponse.notFound();
            }

            if (body == null || !(body.get("result") instanceof Map<?, ?> resultRaw)) {
                return PostalLookupResponse.notFound();
            }

            Map<String, Object> result = (Map<String, Object>) resultRaw;

            String city = null;
            Object adminDistrictObj = result.get("admin_district");
            if (adminDistrictObj instanceof List<?> list && !list.isEmpty()) {
                for (Object item : list) {
                    String d = stringOrNull(item);
                    if (d != null && (d.toLowerCase().contains("london") || d.equalsIgnoreCase("westminster"))) {
                        city = "London";
                        break;
                    }
                }
                if (city == null) {
                    city = stringOrNull(list.get(0));
                }
            } else if (adminDistrictObj != null) {
                city = stringOrNull(adminDistrictObj);
            }

            String state = "England";
            Object countryObj = result.get("country");
            if (countryObj instanceof List<?> list && !list.isEmpty()) {
                state = stringOrNull(list.get(0));
            } else if (countryObj != null) {
                state = stringOrNull(countryObj);
            }

            if (city == null) {
                return PostalLookupResponse.notFound();
            }

            return PostalLookupResponse.builder()
                    .found(true)
                    .city(city)
                    .state(state != null ? state : "England")
                    .country("United Kingdom")
                    .build();
        }

        // Full postcode lookup (e.g. SW1A 1AA or SW1A1AA)
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(ukBaseUrl + "/postcodes/{code}", normalized)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            return PostalLookupResponse.notFound();
        }

        if (body == null || !(body.get("result") instanceof Map<?, ?> resultRaw)) {
            return PostalLookupResponse.notFound();
        }

        Map<String, Object> result = (Map<String, Object>) resultRaw;

        String region = stringOrNull(result.get("region"));
        String adminDistrict = stringOrNull(result.get("admin_district"));

        String city = "London".equalsIgnoreCase(region) ? "London" : adminDistrict;

        if (city == null) {
            return PostalLookupResponse.notFound();
        }

        return PostalLookupResponse.builder()
                .found(true)
                .city(city)
                .state(stringOrNull(result.get("country")))
                .country("United Kingdom")
                .build();
    }

    @SuppressWarnings("unchecked")
    private PostalLookupResponse lookupZippopotam(String countryCode, String postalCode) {

        Map<String, Object> body = restClient.get()
                .uri(zippopotamBaseUrl + "/{cc}/{zip}", countryCode, postalCode)
                .retrieve()
                .body(Map.class);

        if (body == null) {
            return PostalLookupResponse.notFound();
        }

        Object placesRaw = body.get("places");

        if (!(placesRaw instanceof List<?> places) || places.isEmpty()) {
            return PostalLookupResponse.notFound();
        }

        Map<String, Object> chosen = "au".equals(countryCode)
                ? findAuCapitalOrFirst((List<Map<String, Object>>) (List<?>) places)
                : (Map<String, Object>) places.get(0);

        return PostalLookupResponse.builder()
                .found(true)
                .city(stringOrNull(chosen.get("place name")))
                .state(stringOrNull(chosen.get("state")))
                .country(stringOrNull(body.get("country")))
                .build();
    }

    private Map<String, Object> findAuCapitalOrFirst(List<Map<String, Object>> places) {

        for (Map<String, Object> place : places) {

            String name = stringOrNull(place.get("place name"));

            if (name != null && AU_CAPITALS.contains(name.toLowerCase())) {
                return place;
            }
        }

        return places.get(0);
    }

    private String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
