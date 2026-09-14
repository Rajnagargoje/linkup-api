package com.linkup.user.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PhotoStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${app.upload.dir:uploads/photos}")
    private String uploadDir;

    // What clients use to actually fetch the file — must match the
    // resource handler mapping registered in WebMvcConfig.
    @Value("${app.upload.base-url:http://localhost:8081/uploads/photos}")
    private String baseUrl;

    @Value("${app.upload.max-size-mb:5}")
    private long maxSizeMb;

    /**
     * Saves the file and returns the public URL it can be fetched at.
     * Throws IllegalArgumentException on anything the client did wrong
     * (bad type, too large) — handled the same way as other validation
     * errors by GlobalExceptionHandler.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only JPEG, PNG, or WEBP images are allowed");
        }
        if (file.getSize() > maxSizeMb * 1024 * 1024) {
            throw new IllegalArgumentException("Image must be " + maxSizeMb + "MB or smaller");
        }

        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            String extension = switch (file.getContentType()) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> ".jpg";
            };
            // Random filename — never trust/reuse the client-supplied
            // original filename (path traversal, collisions, guessable URLs).
            String filename = UUID.randomUUID() + extension;
            Path target = dir.resolve(filename);

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            return baseUrl + "/" + filename;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store uploaded file", ex);
        }
    }

    /**
     * Best-effort delete — a failure here shouldn't block removing the
     * URL from the user's profile, so callers should catch/ignore.
     */
    public void delete(String photoUrl) {
        if (photoUrl == null || !photoUrl.startsWith(baseUrl)) return;
        String filename = photoUrl.substring(baseUrl.length() + 1);
        try {
            Files.deleteIfExists(Paths.get(uploadDir).resolve(filename));
        } catch (IOException ignored) {
            // Non-critical — an orphaned file on disk isn't worth failing the request over.
        }
    }
}
