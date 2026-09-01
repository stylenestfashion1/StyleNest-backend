package com.stylenest.stylenest_backend.service;

import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.upload.ImageUploadResponse;

/**
 * Local-filesystem storage for admin-uploaded product images. Deliberately
 * not Cloudinary/S3/any external provider -- files live under the
 * configured {@code app.upload.dir}, and the database only ever stores the
 * resulting public URL (via the existing ProductImage.imageUrl field),
 * never binary data.
 */
public interface ImageStorageService {

    /**
     * Validates, stores, and returns the public URL for an uploaded image.
     * Never trusts the client-supplied filename or Content-Type for
     * anything other than a cheap first-pass check -- the actual file
     * bytes are sniffed to confirm they're really a JPEG/PNG/WebP before
     * anything is written to disk.
     */
    ImageUploadResponse store(MultipartFile file);

    /**
     * Deletes the local file backing {@code imageUrl} if (and only if) it
     * was produced by {@link #store}, i.e. lives inside our own configured
     * upload directory. A no-op for any externally hosted URL (Unsplash,
     * a CDN, etc.) -- this must never attempt to delete something outside
     * our own upload directory.
     */
    void deleteIfManaged(String imageUrl);
}
