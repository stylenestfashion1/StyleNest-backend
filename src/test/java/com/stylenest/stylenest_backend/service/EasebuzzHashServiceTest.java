package com.stylenest.stylenest_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EasebuzzHashServiceTest {

    private final EasebuzzHashService hashService = new EasebuzzHashService();

    // Expected values below were computed independently (Python hashlib.sha512
    // over the exact pipe-separated sequence Easebuzz's own PHP SDK builds)
    // so this test is a genuine cross-check of the Java implementation, not a
    // tautology against itself.

    @Test
    void generateInitiateHash_matchesIndependentlyComputedSha512() {

        Map<String, String> fields = new HashMap<>();
        fields.put("key", "KEY123");
        fields.put("txnid", "TXN001");
        fields.put("amount", "100.00");
        fields.put("productinfo", "Feminine21 Order");
        fields.put("firstname", "John Doe");
        fields.put("email", "john@example.com");
        fields.put("udf1", "1");
        // udf2-udf10 intentionally left unset -- must hash as empty strings

        String hash = hashService.generateInitiateHash(fields, "MYSALT");

        assertThat(hash).isEqualTo(
                "a6e56cf1e6d6f7cf269e53dbe54e1de82d3e61edbeb456a5ff74dfb36c5e98f"
                        + "7d1118b9d50a44494502f2fe03de38ae02321fea750ef55eb13a98bb282b24569");
    }

    @Test
    void generateInitiateHash_isLowercase() {

        Map<String, String> fields = Map.of("key", "K", "txnid", "T");

        String hash = hashService.generateInitiateHash(fields, "S");

        assertThat(hash).isEqualTo(hash.toLowerCase());
    }

    @Test
    void verifyResponseHash_acceptsCorrectlySignedResponse() {

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("udf1", "1");
        response.put("email", "john@example.com");
        response.put("firstname", "John Doe");
        response.put("productinfo", "Feminine21 Order");
        response.put("amount", "100.00");
        response.put("txnid", "TXN001");
        response.put("key", "KEY123");
        response.put("hash",
                "56dcaa06a687d67b3c59cc3f3b974714ae52d88083634d108638496e1d897a983"
                        + "d34c42517cd9bfaa07f7797deffa1dd5d5a6f4280e90491199f66383bcc7b98");

        assertThat(hashService.verifyResponseHash(response, "MYSALT")).isTrue();
    }

    @Test
    void verifyResponseHash_rejectsTamperedAmount() {

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("udf1", "1");
        response.put("email", "john@example.com");
        response.put("firstname", "John Doe");
        response.put("productinfo", "Feminine21 Order");
        response.put("amount", "1.00"); // tampered -- was 100.00 when hash was computed
        response.put("txnid", "TXN001");
        response.put("key", "KEY123");
        response.put("hash",
                "56dcaa06a687d67b3c59cc3f3b974714ae52d88083634d108638496e1d897a983"
                        + "d34c42517cd9bfaa07f7797deffa1dd5d5a6f4280e90491199f66383bcc7b98");

        assertThat(hashService.verifyResponseHash(response, "MYSALT")).isFalse();
    }

    @Test
    void verifyResponseHash_rejectsMissingHash() {

        Map<String, String> response = Map.of("status", "success", "txnid", "TXN001");

        assertThat(hashService.verifyResponseHash(response, "MYSALT")).isFalse();
    }

    @Test
    void verifyResponseHash_rejectsWrongSalt() {

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("udf1", "1");
        response.put("email", "john@example.com");
        response.put("firstname", "John Doe");
        response.put("productinfo", "Feminine21 Order");
        response.put("amount", "100.00");
        response.put("txnid", "TXN001");
        response.put("key", "KEY123");
        response.put("hash",
                "56dcaa06a687d67b3c59cc3f3b974714ae52d88083634d108638496e1d897a983"
                        + "d34c42517cd9bfaa07f7797deffa1dd5d5a6f4280e90491199f66383bcc7b98");

        assertThat(hashService.verifyResponseHash(response, "WRONGSALT")).isFalse();
    }

    @Test
    void generateTransactionStatusHash_matchesKeyTxnidSaltSequence() {

        String hash = hashService.generateTransactionStatusHash("KEY123", "TXN001", "MYSALT");

        // Independently computed: sha512("KEY123|TXN001|MYSALT")
        assertThat(hash).isEqualTo(
                "4105305ff8ba7e1cb787c246ad72f928ad239716c11c574423604eda9d77670"
                        + "041f8ac05318e85deb70280c49ef5b2a5e7beb3bc4a1f04491d1c71a32c43c319");
    }
}
