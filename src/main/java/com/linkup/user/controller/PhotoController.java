package com.linkup.user.controller;

import com.linkup.user.dto.response.ApiResponseDTO;
import com.linkup.user.dto.response.UserDTO;
import com.linkup.user.entity.User;
import com.linkup.user.exception.ResourceNotFoundException;
import com.linkup.user.mapper.UserMapper;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.impl.PhotoStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/user/me/photos")
@RequiredArgsConstructor
public class PhotoController {

    private static final int MAX_PHOTOS = 6;

    private final PhotoStorageService photoStorageService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponseDTO<UserDTO>> uploadPhoto(
            Principal principal,
            @RequestParam("file") MultipartFile file
    ) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getPhotos().size() >= MAX_PHOTOS) {
            throw new IllegalArgumentException("You can add up to " + MAX_PHOTOS + " photos");
        }

        String url = photoStorageService.store(file);
        user.getPhotos().add(url);

        // First photo uploaded becomes the profile photo by default —
        // the person can change it later, this just avoids an empty
        // profile picture after finishing the photos step.
        if (user.getProfilePhoto() == null) {
            user.setProfilePhoto(url);
        }

        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDTO<>(HttpStatus.CREATED.value(), "Photo uploaded", userMapper.toUserDTO(user)));
    }

    @DeleteMapping
    @Transactional
    public ResponseEntity<ApiResponseDTO<UserDTO>> deletePhoto(
            Principal principal,
            @RequestParam("url") String url
    ) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean removed = user.getPhotos().remove(url);
        if (!removed) {
            throw new IllegalArgumentException("That photo isn't on your profile");
        }

        if (url.equals(user.getProfilePhoto())) {
            user.setProfilePhoto(user.getPhotos().isEmpty() ? null : user.getPhotos().get(0));
        }

        userRepository.save(user);
        photoStorageService.delete(url); // best-effort; failure here doesn't roll back the profile change

        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "Photo removed", userMapper.toUserDTO(user)));
    }
}
