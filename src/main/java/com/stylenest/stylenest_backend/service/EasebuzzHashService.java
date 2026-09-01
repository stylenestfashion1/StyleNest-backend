package com.stylenest.stylenest_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * SHA-512 hash generation/verification for the Easebuzz payment gateway,
 * per Easebuzz's official field sequences (source: easebuzz/paywitheasebuzz-php-lib,
 * easebuzz-lib/utils.php -- generateHashValue()/_getReverseHashKey()):
 *
 * Initiate Payment API request hash:
 *   key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|udf6|udf7|udf8|udf9|udf10|salt
 *
 * Response (SURL/FURL) reverse hash:
 *   salt|status|udf10|udf9|udf8|udf7|udf6|udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key
 *
 * A missing field is hashed as an empty string (the pipe separator is still
 * included) -- required for the hash to match on Easebuzz's side even when
 * optional fields like udf2-udf10 are unused.
 */
@Component
public class EasebuzzHashService {

    private static final List<String> INITIATE_HASH_FIELDS = List.of(
            "key", "txnid", "amount", "productinfo", "firstname", "email",
            "udf1", "udf2", "udf3", "udf4", "udf5", "udf6", "udf7", "udf8", "udf9", "udf10");

    private static final List<String> RESPONSE_HASH_FIELDS_REVERSE = List.of(
            "udf10", "udf9", "udf8", "udf7", "udf6", "udf5", "udf4", "udf3", "udf2", "udf1",
            "email", "firstname", "productinfo", "amount", "txnid", "key");

    public String generateInitiateHash(Map<String, String> fields, String salt) {

        StringBuilder sb = new StringBuilder();

        for (String field : INITIATE_HASH_FIELDS) {
            sb.append(nullToEmpty(fields.get(field))).append('|');
        }

        sb.append(nullToEmpty(salt));

        return sha512Lower(sb.toString());
    }

    /**
     * Verifies the reverse hash on an Easebuzz SURL/FURL response. Returns
     * false (never throws) for a missing/malformed hash so callers can
     * treat any failure uniformly as "not verified".
     */
    public boolean verifyResponseHash(Map<String, String> responseFields, String salt) {

        String provided = responseFields.get("hash");

        if (provided == null || provided.isBlank()) {
            return false;
        }

        StringBuilder sb = new StringBuilder();

        sb.append(nullToEmpty(salt)).append('|').append(nullToEmpty(responseFields.get("status")));

        for (String field : RESPONSE_HASH_FIELDS_REVERSE) {
            sb.append('|').append(nullToEmpty(responseFields.get(field)));
        }

        String expected = sha512Lower(sb.toString());

        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    /**
     * Hash for the Transaction Status API (transaction/v2/retrieve).
     * Sequence per the official SDK: key|txnid|salt.
     */
    public String generateTransactionStatusHash(String key, String txnid, String salt) {

        String hashString = nullToEmpty(key) + "|" + nullToEmpty(txnid) + "|" + nullToEmpty(salt);

        return sha512Lower(hashString);
    }

    private String sha512Lower(String input) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-512");

            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hashBytes.length * 2);

            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException("SHA-512 algorithm not available on this JVM.", e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
