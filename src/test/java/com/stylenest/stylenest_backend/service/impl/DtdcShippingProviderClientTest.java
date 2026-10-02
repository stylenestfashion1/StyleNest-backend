package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.exception.DtdcApiException;
import com.stylenest.stylenest_backend.service.courier.CourierTrackingService.ShipmentBookingRequest;

/**
 * bookShipment/cancelShipment/fetchLabel/fetchTracking all make real HTTP
 * calls and are only exercised here for their "not configured" fail-fast
 * path, same as CashfreePaymentProviderClientTest does for Cashfree --
 * the actual DTDC round-trip needs real sandbox/production credentials and
 * is covered by manual/E2E testing once those are supplied. mapDtdcStatus
 * (the documented DTDC-status -> ShipmentStatus mapping) is pure logic and
 * is fully covered with no network involved.
 */
class DtdcShippingProviderClientTest {

    private final DtdcShippingProviderClient client = new DtdcShippingProviderClient(
            "", "", "B2C PRIORITY", "Apparel", "", "", "production",
            "StyleNest Fashion", "6269933231", "Madhya Pradesh",
            "175-B, Amrit Palace, Nipania", "Indore", "452010");

    private ShipmentBookingRequest sampleRequest() {
        return new ShipmentBookingRequest(
                "SN-1", "Test Customer", "9999999999", "Line 1", "",
                "Salem", "Tamil Nadu", "636010",
                true, new BigDecimal("1500.00"), new BigDecimal("1500.00"),
                new BigDecimal("1.0"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"), 1);
    }

    @Test
    void bookShipment_whenNotConfigured_failsCleanlyWithoutAnyNetworkCall() {

        assertThatThrownBy(() -> client.bookShipment(sampleRequest()))
                .isInstanceOf(DtdcApiException.class);
    }

    @Test
    void cancelShipment_whenNotConfigured_failsCleanlyWithoutAnyNetworkCall() {

        assertThatThrownBy(() -> client.cancelShipment("7X100761088"))
                .isInstanceOf(DtdcApiException.class);
    }

    @Test
    void fetchLabel_whenNotConfigured_failsCleanlyWithoutAnyNetworkCall() {

        assertThatThrownBy(() -> client.fetchLabel("7X100761088"))
                .isInstanceOf(DtdcApiException.class);
    }

    @Test
    void fetchTracking_whenTrackingCredentialsNotConfigured_failsCleanlyWithoutAnyNetworkCall() {

        assertThatThrownBy(() -> client.fetchTracking("DTDC", "7X100761088"))
                .isInstanceOf(DtdcApiException.class);
    }

    @Test
    void fetchTracking_unrecognizedCourierName_returnsEmptyWithoutCallingDtdc() {

        assertThat(client.fetchTracking("Bluedart", "TRK123")).isEmpty();
    }

    // ---- mapDtdcStatus: the documented DTDC status vocabulary -> ShipmentStatus ----

    @Test
    void mapDtdcStatus_delivered_mapsToDelivered() {
        assertThat(client.mapDtdcStatus("Delivered", "Delivered")).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void mapDtdcStatus_rto_mapsToReturned() {
        assertThat(client.mapDtdcStatus("RTO", "Consignment Returned")).isEqualTo(ShipmentStatus.RETURNED);
    }

    @Test
    void mapDtdcStatus_deliveryInProgressWithOutForDeliveryAction_mapsToOutForDelivery() {
        assertThat(client.mapDtdcStatus("DELIVERY PROCESS IN PROGRESS", "Out For Delivery"))
                .isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void mapDtdcStatus_deliveryInProgressWithoutOutForDeliveryAction_mapsToInTransit() {
        assertThat(client.mapDtdcStatus("DELIVERY PROCESS IN PROGRESS", "Received"))
                .isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void mapDtdcStatus_attempted_mapsToInTransit() {
        assertThat(client.mapDtdcStatus("ATTEMPTED", "Not Delivered")).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void mapDtdcStatus_heldup_mapsToInTransit() {
        assertThat(client.mapDtdcStatus("HELDUP", "Heldup")).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void mapDtdcStatus_pickedUpAction_mapsToShipped() {
        assertThat(client.mapDtdcStatus("", "Picked Up")).isEqualTo(ShipmentStatus.SHIPPED);
    }

    @Test
    void mapDtdcStatus_prePickupActions_mapToPacked() {
        assertThat(client.mapDtdcStatus("", "Pickup Awaited")).isEqualTo(ShipmentStatus.PACKED);
        assertThat(client.mapDtdcStatus("", "Pickup Scheduled")).isEqualTo(ShipmentStatus.PACKED);
        assertThat(client.mapDtdcStatus("", "Booked")).isEqualTo(ShipmentStatus.PACKED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildBookingBody_codShipment_includesRequiredFields() {

        var body = client.buildBookingBody(sampleRequest());
        var consignments = (java.util.List<java.util.Map<String, Object>>) body.get("consignments");
        assertThat(consignments).hasSize(1);

        var consignment = consignments.get(0);
        assertThat(consignment.get("description")).isEqualTo("");
        assertThat(consignment.get("consignment_type")).isEqualTo("Forward");
        assertThat(consignment.get("dimension_unit")).isEqualTo("CM");
        assertThat(consignment.get("weight_unit")).isEqualTo("KG");
        assertThat(consignment.get("is_risk_surcharge_applicable")).isEqualTo(false);
        assertThat(consignment.get("cod_favor_of")).isEqualTo("");

        var origin = (java.util.Map<String, Object>) consignment.get("origin_details");
        assertThat(origin.get("alternate_phone")).isEqualTo("");

        var destination = (java.util.Map<String, Object>) consignment.get("destination_details");
        assertThat(destination.get("alternate_phone")).isEqualTo("");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildBookingBody_prepaidShipment_includesCodFavorOfAsEmptyString() {

        var prepaidRequest = new ShipmentBookingRequest(
                "SN-2", "Prepaid Customer", "9999999999", "Line 1", "",
                "Salem", "Tamil Nadu", "636010",
                false, null, new BigDecimal("1500.00"),
                new BigDecimal("1.0"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"), 1);

        var body = client.buildBookingBody(prepaidRequest);
        var consignments = (java.util.List<java.util.Map<String, Object>>) body.get("consignments");
        var consignment = consignments.get(0);

        assertThat(consignment.get("description")).isEqualTo("");
        assertThat(consignment.get("cod_favor_of")).isEqualTo("");

        var origin = (java.util.Map<String, Object>) consignment.get("origin_details");
        assertThat(origin.get("alternate_phone")).isEqualTo("");

        var destination = (java.util.Map<String, Object>) consignment.get("destination_details");
        assertThat(destination.get("alternate_phone")).isEqualTo("");
    }
}
