package com.linkup.user.dto.response;


import com.linkup.user.utils.Gender;
import com.linkup.user.utils.LookingFor;
import com.linkup.user.utils.Role;
import com.linkup.user.utils.Status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * NOTE: this is used both for /register, /login's follow-up /me call, and
 * "view your own profile". It intentionally excludes password,
 * tokenVersion, failedLoginAttempts, lockedUntil, and fcmToken — none of
 * that belongs in an API response. If you later add "view someone ELSE's
 * profile", use a separate, more restrictive DTO (no email, no exact
 * lat/long) rather than reusing this one.
 */
public record UserDTO(
        String publicId,
        String username,
        String email,
        Role role,
        Status status,
        Boolean online,
        LocalDateTime lastSeenAt,
        Boolean emailVerified,
        Boolean phoneVerified,

        LocalDate dob,
        Integer age,
        Gender gender,
        String bio,
        String profilePhoto,
        List<String> photos,
        List<String> interests,
        Boolean onboardingCompleted,

        LookingFor lookingFor,
        List<Gender> genderPreference,
        Integer minAgePreference,
        Integer maxAgePreference,
        Double maxDistanceKm,

        Double latitude,
        Double longitude,
        Boolean locationVisible,

        LocalDateTime createdAt
) {}
