package com.stylenest.stylenest_backend.service.payglocal;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.stylenest.stylenest_backend.config.PayGlocalProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayGlocalCryptoService {

    private final PayGlocalProperties properties;

    private static final JWEAlgorithm JWE_ALGO = JWEAlgorithm.RSA_OAEP_256;
    private static final EncryptionMethod JWE_ENC = EncryptionMethod.A128CBC_HS256;
    private static final JWSAlgorithm JWS_ALGO = JWSAlgorithm.RS256;
    private static final String DIGEST_ALGO = "SHA-256";
    private static final long EXPIRY_MS = 300000L; // 5 minutes recommended by PayGlocal

    private PublicKey payGlocalPublicKey;
    private PrivateKey merchantPrivateKey;

    @PostConstruct
    public void init() {
        try {
            this.payGlocalPublicKey = loadPublicKey(properties.getPublicKeyPath());
            this.merchantPrivateKey = loadPrivateKey(properties.getPrivateKeyPath());
            log.info("PayGlocal crypto keys loaded successfully (Merchant: {}, Key ID: {})",
                    properties.getMerchantId(), properties.getPrivateKeyId());
        } catch (Exception e) {
            log.error("Failed to initialize PayGlocal crypto keys: {}", e.getMessage(), e);
        }
    }

    public String encryptPayload(String jsonPayload) {
        try {
            if (payGlocalPublicKey == null) {
                this.payGlocalPublicKey = loadPublicKey(properties.getPublicKeyPath());
            }

            JWEHeader header = new JWEHeader.Builder(JWE_ALGO, JWE_ENC)
                    .keyID(properties.getPublicKeyId())
                    .customParam("iat", String.valueOf(Instant.now().toEpochMilli()))
                    .customParam("exp", EXPIRY_MS)
                    .customParam("issued-by", properties.getMerchantId())
                    .build();

            JWEObject jweObject = new JWEObject(header, new Payload(jsonPayload));

            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(JWE_ENC.cekBitLength());
            SecretKey cek = keyGenerator.generateKey();

            jweObject.encrypt(new RSAEncrypter((RSAPublicKey) payGlocalPublicKey, cek));
            return jweObject.serialize();
        } catch (Exception e) {
            log.error("Failed to encrypt PayGlocal JWE payload: {}", e.getMessage(), e);
            throw new RuntimeException("PayGlocal encryption error: " + e.getMessage(), e);
        }
    }

    public String signPayload(String payload) {
        try {
            if (merchantPrivateKey == null) {
                this.merchantPrivateKey = loadPrivateKey(properties.getPrivateKeyPath());
            }

            JWSSigner signer = new RSASSASigner(merchantPrivateKey);

            JWSHeader header = new JWSHeader.Builder(JWS_ALGO)
                    .keyID(properties.getPrivateKeyId())
                    .customParam("x-gl-merchantId", properties.getMerchantId())
                    .customParam("x-gl-enc", "true")
                    .customParam("issued-by", properties.getMerchantId())
                    .customParam("is-digested", "true")
                    .build();

            MessageDigest md = MessageDigest.getInstance(DIGEST_ALGO);
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            String base64Digest = Base64.getEncoder().encodeToString(digest);

            Map<String, Object> claims = new HashMap<>();
            claims.put("digest", base64Digest);
            claims.put("digestAlgorithm", DIGEST_ALGO);
            claims.put("iat", String.valueOf(Instant.now().toEpochMilli()));
            claims.put("exp", EXPIRY_MS);

            JWSObject jwsObject = new JWSObject(header, new Payload(claims));
            jwsObject.sign(signer);
            return jwsObject.serialize();
        } catch (Exception e) {
            log.error("Failed to sign PayGlocal JWS token: {}", e.getMessage(), e);
            throw new RuntimeException("PayGlocal signing error: " + e.getMessage(), e);
        }
    }

    public boolean verifyJwsToken(String jwsToken) {
        try {
            if (payGlocalPublicKey == null) {
                this.payGlocalPublicKey = loadPublicKey(properties.getPublicKeyPath());
            }
            JWSObject jwsObject = JWSObject.parse(jwsToken);
            JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) payGlocalPublicKey);
            return jwsObject.verify(verifier);
        } catch (Exception e) {
            log.error("Failed to verify PayGlocal JWS token: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> parseJwsClaims(String jwsToken) {
        try {
            JWSObject jwsObject = JWSObject.parse(jwsToken);
            return jwsObject.getPayload().toJSONObject();
        } catch (Exception e) {
            log.error("Failed to parse PayGlocal JWS payload claims: {}", e.getMessage());
            return Map.of();
        }
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        String pem = readPemContent(path);
        String clean = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] bytes = Base64.getDecoder().decode(clean);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = readPemContent(path);
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] bytes = Base64.getDecoder().decode(clean);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private String readPemContent(String path) throws Exception {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Key file path is blank");
        }
        File file = new File(path);
        if (file.exists()) {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }
        // Fallback to VPS standard location
        File vpsFile = new File("/opt/stylenest/keys/payglocal/" + file.getName());
        if (vpsFile.exists()) {
            return Files.readString(vpsFile.toPath(), StandardCharsets.UTF_8);
        }
        // Fallback to local project resources
        File localFile = new File("src/main/resources/keys/payglocal/" + file.getName());
        if (localFile.exists()) {
            return Files.readString(localFile.toPath(), StandardCharsets.UTF_8);
        }
        ClassPathResource resource = new ClassPathResource(path);
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Key file not found at path: " + path);
    }
}
