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
 * Country-aware ZIP/postal code -> city/region lookup. Routes to whichever
 * provider gives usable city-level data for a given country -- there is no
 * single free source with good coverage everywhere:
 *
 *  - India (IN): India Post's official pincode API
 *    (api.postalpincode.in). Zippopotam's India dataset has no
 *    city/district field at all -- every entry is a hyper-local
 *    post-office name (e.g. "Haji S Musafarkhana" for Mumbai 400001, never
 *    "Mumbai" itself, at any position) -- so a different source was
 *    required, not just a different field.
 *  - United Kingdom (GB): postcodes.io (ONS open data). Zippopotam only
 *    supports UK "outward" codes (e.g. "SW1A", not full "SW1A 1AA") and
 *    even then returns landmark names ("Buckingham Palace"), never a city.
 *  - Everyone else (US, AU, CA, ...): api.zippopotam.us, unchanged. For
 *    Australia specifically, its data does include a proper city entry per
 *    postcode (e.g. "Sydney" alongside "The Rocks"/"Haymarket" for 2000)
 *    but not always first in the list, so a known state/territory capital
 *    is preferred over places[0] when present. Canada's Zippopotam data is
 *    locality-level only and doesn't resolve full 6-character postal codes
 *    at all; no reliable free/keyless alternative was found (geocoder.ca
 *    throttles to the point of being unusable in production), so CA
 *    deliberately still resolves to not-found -- the frontend already
 *    falls back to manual city/state entry gracefully.
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

        // api.postalpincode.in resets the connection for requests with no
        // (or Java's default) User-Agent -- confirmed live: identical
        // request succeeds once a real User-Agent is set. Every other
        // provider here works fine without one; this is specific to this
        // one API's own basic anti-bot filtering.
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

        Map<String, Object> body = restClient.get()
                .uri(ukBaseUrl + "/postcodes/{code}", postalCode)
                .retrieve()
                .body(Map.class);

        if (body == null || !(body.get("result") instanceof Map<?, ?> resultRaw)) {
            return PostalLookupResponse.notFound();
        }

        Map<String, Object> result = (Map<String, Object>) resultRaw;

        String region = stringOrNull(result.get("region"));
        String adminDistrict = stringOrNull(result.get("admin_district"));

        // "region" is one of 9 broad England-only groupings (North West,
        // South East, ...) -- useless as a city name -- EXCEPT "London",
        // which uniquely is both the region name and the real post town
        // for everything inside it. Everywhere else, admin_district (the
        // borough/city/town, e.g. "Manchester", "Cardiff") is the closer
        // match to what a checkout form's "city" field expects.
        String city = "London".equals(region) ? "London" : adminDistrict;

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

    // Zippopotam's Australian data includes the actual capital city as one
    // of several entries for a postcode (e.g. 2000 lists "The Rocks",
    // "Haymarket", ... AND "Sydney") but not necessarily first -- prefer
    // the capital when present rather than whichever suburb happens to
    // land at places[0].
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
