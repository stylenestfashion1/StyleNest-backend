package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.stylenest.stylenest_backend.dto.postal.PostalLookupResponse;

class CompositePostalLookupServiceImplTest {

    // Deterministic, no live network calls -- keeps CI fast/stable
    // regardless of the real providers' availability.
    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private CompositePostalLookupServiceImpl service;

    private void setUp() {

        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        service = new CompositePostalLookupServiceImpl(builder.build());
        ReflectionTestUtils.setField(service, "zippopotamBaseUrl", "https://api.zippopotam.us");
        ReflectionTestUtils.setField(service, "indiaBaseUrl", "https://api.postalpincode.in");
        ReflectionTestUtils.setField(service, "ukBaseUrl", "https://api.postcodes.io");
    }

    @Test
    void lookup_us_usesZippopotam_returnsCityDirectly() {

        setUp();

        mockServer.expect(requestTo("https://api.zippopotam.us/us/10001"))
                .andRespond(withSuccess(
                        "{\"country\":\"United States\",\"places\":[{\"place name\":\"New York City\",\"state\":\"New York\"}]}",
                        MediaType.APPLICATION_JSON));

        PostalLookupResponse response = service.lookup("us", "10001");

        assertThat(response.isFound()).isTrue();
        assertThat(response.getCity()).isEqualTo("New York City");
        assertThat(response.getState()).isEqualTo("New York");
        assertThat(response.getCountry()).isEqualTo("United States");

        mockServer.verify();
    }

    @Test
    void lookup_au_multipleEntriesWithCapitalPresent_prefersCapitalOverFirstEntry() {

        setUp();

        // Mirrors the real Zippopotam response shape for AU 2000: the
        // capital ("Sydney") is present but not first in the list.
        mockServer.expect(requestTo("https://api.zippopotam.us/au/2000"))
                .andRespond(withSuccess(
                        "{\"country\":\"Australia\",\"places\":["
                                + "{\"place name\":\"The Rocks\",\"state\":\"New South Wales\"},"
                                + "{\"place name\":\"Haymarket\",\"state\":\"New South Wales\"},"
                                + "{\"place name\":\"Sydney\",\"state\":\"New South Wales\"}"
                                + "]}",
                        MediaType.APPLICATION_JSON));

        PostalLookupResponse response = service.lookup("au", "2000");

        assertThat(response.isFound()).isTrue();
        assertThat(response.getCity()).isEqualTo("Sydney");

        mockServer.verify();
    }

    @Test
    void lookup_au_noCapitalInList_fallsBackToFirstEntry() {

        setUp();

        mockServer.expect(requestTo("https://api.zippopotam.us/au/9999"))
                .andRespond(withSuccess(
                        "{\"country\":\"Australia\",\"places\":["
                                + "{\"place name\":\"Some Regional Suburb\",\"state\":\"Victoria\"}"
                                + "]}",
                        MediaType.APPLICATION_JSON));

        PostalLookupResponse response = service.lookup("au", "9999");

        assertThat(response.isFound()).isTrue();
        assertThat(response.getCity()).isEqualTo("Some Regional Suburb");

        mockServer.verify();
    }

    @Test
    void lookup_india_usesDistrictFieldNotLocalityName() {

        setUp();

        // api.postalpincode.in resets the connection without a real
        // User-Agent header (confirmed live) -- assert it's actually sent.
        mockServer.expect(requestTo("https://api.postalpincode.in/pincode/452001"))
                .andExpect(header("User-Agent", "StyleNest-Backend/1.0"))
                .andRespond(withSuccess(
                        "[{\"Status\":\"Success\",\"PostOffice\":["
                                + "{\"Name\":\"Indore Jail Road\",\"District\":\"Indore\",\"State\":\"Madhya Pradesh\",\"Country\":\"India\"}"
                                + "]}]",
                        MediaType.APPLICATION_JSON));

        PostalLookupResponse response = service.lookup("in", "452001");

        assertThat(response.isFound()).isTrue();
        assertThat(response.getCity()).isEqualTo("Indore");
        assertThat(response.getState()).isEqualTo("Madhya Pradesh");
        assertThat(response.getCountry()).isEqualTo("India");

        mockServer.verify();
    }

    @Test
    void lookup_india_sourceReturnsErrorStatus_returnsFoundFalse() {

        setUp();

        // India Post's API returns HTTP 200 with Status:"Error" for
        // unknown pincodes, not a 404 -- must be handled explicitly.
        mockServer.expect(requestTo("https://api.postalpincode.in/pincode/000000"))
                .andRespond(withSuccess(
                        "[{\"Message\":\"No records found\",\"Status\":\"Error\",\"PostOffice\":null}]",
                        MediaType.APPLICATION_JSON));

        PostalLookupResponse response = service.lookup("in", "000000");

        assertThat(response.isFound()).isFalse();

        mockServer.verify();
    }

    @Test
    void lookup_uk_regionIsLondon_cityIsLondonNotBorough() {

        setUp();

        mockServer.expect(requestTo("https://api.postcodes.io/postcodes/SW1A1AA"))
                .andRespond(withSuccess(
                        "{\"status\":200,\"result\":{\"region\":\"London\",\"admin_district\":\"Westminster\",\"country\":\"England\"}}",
                        MediaType.APPLICATION_JSON));

        PostalLookupResponse response = service.lookup("gb", "SW1A1AA");

        assertThat(response.isFound()).isTrue();
        assertThat(response.getCity()).isEqualTo("London");
        assertThat(response.getState()).isEqualTo("England");
        assertThat(response.getCountry()).isEqualTo("United Kingdom");

        mockServer.verify();
    }

    @Test
    void lookup_uk_regionOutsideLondon_usesAdminDistrict() {

        setUp();

        mockServer.expect(requestTo("https://api.postcodes.io/postcodes/M11AE"))
                .andRespond(withSuccess(
                        "{\"status\":200,\"result\":{\"region\":\"North West\",\"admin_district\":\"Manchester\",\"country\":\"England\"}}",
                        MediaType.APPLICATION_JSON));

        PostalLookupResponse response = service.lookup("gb", "M11AE");

        assertThat(response.isFound()).isTrue();
        assertThat(response.getCity()).isEqualTo("Manchester");

        mockServer.verify();
    }

    @Test
    void lookup_uk_notFound_returnsFoundFalse_doesNotThrow() {

        setUp();

        mockServer.expect(requestTo("https://api.postcodes.io/postcodes/ZZ999ZZ"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        PostalLookupResponse response = service.lookup("gb", "ZZ999ZZ");

        assertThat(response.isFound()).isFalse();
    }

    @Test
    void lookup_ca_fullPostalCodeNotResolvable_returnsFoundFalse_deliberateScopeGap() {

        setUp();

        // Confirmed live: Zippopotam has no data for full 6-character
        // Canadian postal codes. No reliable free/keyless alternative was
        // found, so CA deliberately still resolves to not-found.
        mockServer.expect(requestTo("https://api.zippopotam.us/ca/K1A0B1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        PostalLookupResponse response = service.lookup("ca", "K1A0B1");

        assertThat(response.isFound()).isFalse();

        mockServer.verify();
    }

    @Test
    void lookup_notFoundResponse_returnsFoundFalse_doesNotThrow() {

        setUp();

        mockServer.expect(requestTo("https://api.zippopotam.us/us/00000"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        PostalLookupResponse response = service.lookup("us", "00000");

        assertThat(response.isFound()).isFalse();
    }

    @Test
    void lookup_serverError_returnsFoundFalse_doesNotThrow() {

        setUp();

        mockServer.expect(requestTo("https://api.zippopotam.us/us/10001"))
                .andRespond(withServerError());

        PostalLookupResponse response = service.lookup("us", "10001");

        assertThat(response.isFound()).isFalse();
    }

    @Test
    void lookup_malformedJson_returnsFoundFalse_doesNotThrow() {

        setUp();

        mockServer.expect(requestTo("https://api.zippopotam.us/us/10001"))
                .andRespond(withSuccess("not json at all", MediaType.APPLICATION_JSON));

        PostalLookupResponse response = service.lookup("us", "10001");

        assertThat(response.isFound()).isFalse();
    }

    @Test
    void lookup_blankInputs_returnsFoundFalse_neverCallsNetwork() {

        setUp();

        assertThat(service.lookup("", "452001").isFound()).isFalse();
        assertThat(service.lookup("in", "").isFound()).isFalse();
        assertThat(service.lookup(null, "452001").isFound()).isFalse();

        mockServer.verify(); // no expectations set -- confirms zero requests were made
    }
}
