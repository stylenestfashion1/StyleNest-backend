package com.stylenest.stylenest_backend.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves admin-uploaded product images straight off the local filesystem
 * at /uploads/** -- the same app.upload.dir used by
 * LocalImageStorageServiceImpl, so this and the storage service always
 * agree on where files actually live. Publicly readable (see
 * SecurityConfig's permitAll for GET /uploads/**), same as any other
 * product image URL.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        String uploadLocation = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);
    }
}
