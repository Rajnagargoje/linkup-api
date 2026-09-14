package com.linkup.user.dto.request;

import com.linkup.user.utils.Gender;
import com.linkup.user.utils.LookingFor;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * All fields optional/nullable — this backs a PATCH, so a client only
 * sends what it's actually changing (e.g. onboarding sends everything
 * once; a later "edit bio" screen sends just `bio`).
 */
public record UpdateProfileDTO(

        @Past(message = "Date of birth must be in the past")
        LocalDate dob,

        Gender gender,

        @Size(max = 500, message = "Bio must be 500 characters or fewer")
        String bio,

        String profilePhoto,

        @Size(max = 6, message = "You can add up to 6 photos")
        List<String> photos,

        @Size(max = 20, message = "You can select up to 20 interests")
        List<String> interests,

        LookingFor lookingFor,

        List<Gender> genderPreference,

        Integer minAgePreference,

        Integer maxAgePreference,

        Double maxDistanceKm
) {
    // Server-side, not client-supplied: 18+ is enforced here regardless
    // of what the app's date picker allows, in case a request bypasses it.
    @AssertTrue(message = "You must be 18 or older to use LinkUp")
    public boolean isAdult() {
        if (dob == null) return true; // not being changed in this request
        return dob.isBefore(LocalDate.now().minusYears(18).plusDays(1));
    }
}
