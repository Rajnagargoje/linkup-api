package com.linkup.user.dto.chat;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactionRequest(

        @NotBlank
        @Size(max = 20)
        String reaction
) {
}