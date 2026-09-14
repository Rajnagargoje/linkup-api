package com.linkup.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRoomRequest(

        @NotBlank(message = "roomId cannot be blank")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "roomId must be 3-32 chars, letters/numbers/underscore/hyphen only")
        String roomId
) {}
