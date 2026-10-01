package com.stylenest.stylenest_backend.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.upload.ImageUploadResponse;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.service.RentalImageStorageService;

/**
 * Mirrors LocalImageStorageServiceImpl's security model exactly (magic-byte
 * sniffing, UUID-only generated filenames, no trust in client filename/
 * Content-Type, path-traversal guards) but targets a separate "rental"
 * subdirectory under the same app.upload.dir root -- see
 * RentalImageStorageService's javadoc for why this is a second class
 * rather than a second implementation of the product ImageStorageService.
 */
@Service
public class RentalImageStorageServiceImpl implements RentalImageStorageService {

    private static final String RENTAL_SUBDIR = "rental";
    private static final String PUBLIC_PATH_PREFIX = "/uploads/" + RENTAL_SUBDIR + "/";

    private static final Pattern SAFE_GENERATED_FILENAME =
            Pattern.compile("^[a-fA-F0-9\\-]{36}\\.(jpg|png|webp)$");

    @Value("${app.upload.dir}")
    private String uploadDirProperty;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Override
    public ImageUploadResponse store(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty.");
        }

        String extension = sniffExtension(file);

        String filename = UUID.randomUUID() + "." + extension;

        Path rentalDir = rentalDirectory();

        try {

            Files.createDirectories(rentalDir);

            Path target = rentalDir.resolve(filename).normalize();

            if (!target.startsWith(rentalDir)) {
                throw new BadRequestException("Invalid upload target.");
            }

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store uploaded image.", ex);
        }

        return ImageUploadResponse.builder()
                .url(appBaseUrl + PUBLIC_PATH_PREFIX + filename)
                .filename(filename)
                .build();
    }

    @Override
    public void deleteIfManaged(String imageUrl) {

        if (imageUrl == null) {
            return;
        }

        String expectedPrefix = appBaseUrl + PUBLIC_PATH_PREFIX;

        if (!imageUrl.startsWith(expectedPrefix)) {
            return;
        }

        String filename = imageUrl.substring(expectedPrefix.length());

        if (!SAFE_GENERATED_FILENAME.matcher(filename).matches()) {
            return;
        }

        Path rentalDir = rentalDirectory();
        Path target = rentalDir.resolve(filename).normalize();

        if (!target.startsWith(rentalDir)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete local image file.", ex);
        }
    }

    private Path rentalDirectory() {
        return Path.of(uploadDirProperty, RENTAL_SUBDIR).normalize();
    }

    // -- Content sniffing (identical rules to LocalImageStorageServiceImpl) --

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF_MAGIC = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC = {0x57, 0x45, 0x42, 0x50};

    private String sniffExtension(MultipartFile file) {

        byte[] header;

        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(12);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded file.", ex);
        }

        if (startsWith(header, JPEG_MAGIC)) {
            return "jpg";
        }

        if (startsWith(header, PNG_MAGIC)) {
            return "png";
        }

        if (header.length >= 12
                && startsWith(header, RIFF_MAGIC)
                && regionEquals(header, 8, WEBP_MAGIC)) {
            return "webp";
        }

        throw new BadRequestException(
                "Unsupported or invalid image file. Only JPG, PNG, and WebP are allowed.");
    }

    private boolean startsWith(byte[] data, byte[] prefix) {

        if (data.length < prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private boolean regionEquals(byte[] data, int offset, byte[] expected) {

        if (data.length < offset + expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }

        return true;
    }
}
