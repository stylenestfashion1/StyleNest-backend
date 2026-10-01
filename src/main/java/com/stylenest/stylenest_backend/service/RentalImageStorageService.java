package com.stylenest.stylenest_backend.service;

import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.upload.ImageUploadResponse;

/**
 * Local-filesystem storage for rental-catalog lehenga photos, kept
 * completely separate from {@link ImageStorageService} (which is
 * product-catalog only). Deliberately its OWN interface/bean rather than a
 * second implementation of ImageStorageService -- Spring would otherwise
 * have two beans of that type and break every existing @Autowired-by-type
 * injection site (e.g. AdminImageUploadController). Files are written under
 * a "rental" subdirectory of the same app.upload.dir root, so a rental
 * image can never collide with, or be deleted alongside, a product image.
 */
public interface RentalImageStorageService {

    ImageUploadResponse store(MultipartFile file);

    void deleteIfManaged(String imageUrl);
}
