package com.linkup.user.service.impl;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name = "app.upload.provider", havingValue = "cloudinary")
public class CloudinaryPhotoStorage {
    private final Cloudinary cloudinary;
    private static final String FOLDER = "linkup/photos/";

    public String store(MultipartFile file) throws IOException {
        var result = cloudinary.uploader().upload(file.getBytes(), Map.of(
            "resource_type", "image", "public_id", FOLDER + UUID.randomUUID(),
            "overwrite", false, "allowed_formats", new String[]{"jpg", "jpeg", "png", "webp"}));
        Object url = result.get("secure_url");
        if (!(url instanceof String value) || !value.startsWith("https://res.cloudinary.com/"))
            throw new IOException("Image provider did not return an HTTPS delivery URL.");
        return value;
    }

    public void delete(String url) throws IOException {
        if (url == null) return;
        // Delete only this app's generated image IDs, never arbitrary remote assets.
        var matcher = Pattern.compile("^https://res\\.cloudinary\\.com/" +
            Pattern.quote(cloudinary.config.cloudName) + "/image/upload/(?:v[0-9]+/)?(" +
            FOLDER + "[0-9a-fA-F-]{36})\\.(?:jpg|jpeg|png|webp)$").matcher(url);
        if (matcher.matches()) cloudinary.uploader().destroy(matcher.group(1),
            Map.of("resource_type", "image", "invalidate", true));
    }
}
