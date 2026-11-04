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
                case "SG" -> lookupSingapore(code);
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

    private static final Map<String, String[]> SG_SECTORS = Map.ofEntries(
            Map.entry("01", new String[]{"Raffles Place / Marina", "Central Singapore"}),
            Map.entry("02", new String[]{"Raffles Place / Marina", "Central Singapore"}),
            Map.entry("03", new String[]{"Raffles Place / Marina", "Central Singapore"}),
            Map.entry("04", new String[]{"Raffles Place / Marina", "Central Singapore"}),
            Map.entry("05", new String[]{"Downtown / Marina", "Central Singapore"}),
            Map.entry("06", new String[]{"Downtown / City Hall", "Central Singapore"}),
            Map.entry("07", new String[]{"Anson / Tanjong Pagar", "Central Singapore"}),
            Map.entry("08", new String[]{"Tanjong Pagar / Chinatown", "Central Singapore"}),
            Map.entry("09", new String[]{"HarbourFront / Telok Blangah", "South Singapore"}),
            Map.entry("10", new String[]{"HarbourFront / Sentosa", "South Singapore"}),
            Map.entry("11", new String[]{"Pasir Panjang", "South-West Singapore"}),
            Map.entry("12", new String[]{"Pasir Panjang / Clementi", "South-West Singapore"}),
            Map.entry("13", new String[]{"Clementi New Town", "West Singapore"}),
            Map.entry("14", new String[]{"Queenstown", "Central Singapore"}),
            Map.entry("15", new String[]{"Queenstown / Tiong Bahru", "Central Singapore"}),
            Map.entry("16", new String[]{"Tiong Bahru / Bukit Merah", "Central Singapore"}),
            Map.entry("17", new String[]{"High Street / Beach Road", "Central Singapore"}),
            Map.entry("18", new String[]{"Middle Road / Bugis", "Central Singapore"}),
            Map.entry("19", new String[]{"Golden Mile / Bugis", "Central Singapore"}),
            Map.entry("20", new String[]{"Little India / Farrer Park", "Central Singapore"}),
            Map.entry("21", new String[]{"Little India / Rochor", "Central Singapore"}),
            Map.entry("22", new String[]{"Orchard / Somerset", "Central Singapore"}),
            Map.entry("23", new String[]{"Orchard / River Valley", "Central Singapore"}),
            Map.entry("24", new String[]{"Tanglin / Grange Road", "Central Singapore"}),
            Map.entry("25", new String[]{"Tanglin / Botanic Gardens", "Central Singapore"}),
            Map.entry("26", new String[]{"Bukit Timah / Holland", "Central Singapore"}),
            Map.entry("27", new String[]{"Holland Village", "Central Singapore"}),
            Map.entry("28", new String[]{"Watten / Dunearn", "Central Singapore"}),
            Map.entry("29", new String[]{"Novena", "Central Singapore"}),
            Map.entry("30", new String[]{"Thomson / Balestier", "Central Singapore"}),
            Map.entry("31", new String[]{"Toa Payoh", "Central Singapore"}),
            Map.entry("32", new String[]{"Toa Payoh / Balestier", "Central Singapore"}),
            Map.entry("33", new String[]{"Serangoon", "North-East Singapore"}),
            Map.entry("34", new String[]{"Macpherson / Potong Pasir", "Central Singapore"}),
            Map.entry("35", new String[]{"Macpherson", "Central Singapore"}),
            Map.entry("36", new String[]{"Aljunied", "Central Singapore"}),
            Map.entry("37", new String[]{"Aljunied / Macpherson", "Central Singapore"}),
            Map.entry("38", new String[]{"Geylang", "East Singapore"}),
            Map.entry("39", new String[]{"Geylang / Mountbatten", "East Singapore"}),
            Map.entry("40", new String[]{"Geylang / Eunos", "East Singapore"}),
            Map.entry("41", new String[]{"Eunos / Kembangan", "East Singapore"}),
            Map.entry("42", new String[]{"Katong / Joo Chiat", "East Singapore"}),
            Map.entry("43", new String[]{"Katong / Tanjong Rhu", "East Singapore"}),
            Map.entry("44", new String[]{"Marine Parade", "East Singapore"}),
            Map.entry("45", new String[]{"Marine Parade / Siglap", "East Singapore"}),
            Map.entry("46", new String[]{"Bedok", "East Singapore"}),
            Map.entry("47", new String[]{"Bedok / Upper East Coast", "East Singapore"}),
            Map.entry("48", new String[]{"Bedok / Eastwood", "East Singapore"}),
            Map.entry("49", new String[]{"Loyang / Changi", "East Singapore"}),
            Map.entry("50", new String[]{"Changi / Flora", "East Singapore"}),
            Map.entry("51", new String[]{"Pasir Ris", "East Singapore"}),
            Map.entry("52", new String[]{"Tampines", "East Singapore"}),
            Map.entry("53", new String[]{"Hougang", "North-East Singapore"}),
            Map.entry("54", new String[]{"Sengkang", "North-East Singapore"}),
            Map.entry("55", new String[]{"Serangoon Garden", "North-East Singapore"}),
            Map.entry("56", new String[]{"Bishan", "Central Singapore"}),
            Map.entry("57", new String[]{"Ang Mo Kio", "North-East Singapore"}),
            Map.entry("58", new String[]{"Upper Bukit Timah", "West Singapore"}),
            Map.entry("59", new String[]{"Upper Bukit Timah / Hillview", "West Singapore"}),
            Map.entry("60", new String[]{"Jurong East", "West Singapore"}),
            Map.entry("61", new String[]{"Jurong West", "West Singapore"}),
            Map.entry("62", new String[]{"Jurong West / Boon Lay", "West Singapore"}),
            Map.entry("63", new String[]{"Jurong / Tuas", "West Singapore"}),
            Map.entry("64", new String[]{"Jurong / Pioneer", "West Singapore"}),
            Map.entry("65", new String[]{"Hillview / Bukit Batok", "West Singapore"}),
            Map.entry("66", new String[]{"Bukit Batok", "West Singapore"}),
            Map.entry("67", new String[]{"Bukit Panjang", "North-West Singapore"}),
            Map.entry("68", new String[]{"Choa Chu Kang", "North-West Singapore"}),
            Map.entry("69", new String[]{"Tengah", "West Singapore"}),
            Map.entry("70", new String[]{"Lim Chu Kang", "North-West Singapore"}),
            Map.entry("71", new String[]{"Lim Chu Kang", "North-West Singapore"}),
            Map.entry("72", new String[]{"Kranji", "North Singapore"}),
            Map.entry("73", new String[]{"Woodlands", "North Singapore"}),
            Map.entry("75", new String[]{"Sembawang", "North Singapore"}),
            Map.entry("76", new String[]{"Yishun", "North Singapore"}),
            Map.entry("77", new String[]{"Upper Thomson", "North Singapore"}),
            Map.entry("78", new String[]{"Springleaf", "North Singapore"}),
            Map.entry("79", new String[]{"Seletar", "North-East Singapore"}),
            Map.entry("80", new String[]{"Seletar", "North-East Singapore"}),
            Map.entry("81", new String[]{"Changi Airport", "East Singapore"}),
            Map.entry("82", new String[]{"Punggol", "North-East Singapore"})
    );

    private PostalLookupResponse lookupSingapore(String postalCode) {
        String clean = postalCode.trim();
        if (!clean.matches("\\d{6}")) {
            return PostalLookupResponse.notFound();
        }
        String sector = clean.substring(0, 2);
        String[] location = SG_SECTORS.getOrDefault(sector, new String[]{"Singapore", "Central Singapore"});

        return PostalLookupResponse.builder()
                .found(true)
                .city(location[0])
                .state(location[1])
                .country("Singapore")
                .build();
    }

    private String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
