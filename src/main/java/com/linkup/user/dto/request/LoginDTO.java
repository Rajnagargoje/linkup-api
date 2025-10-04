package com.linkup.user.dto.request;


import jakarta.validation.constraints.NotBlank;

public record LoginDTO(

        @NotBlank(message = "Username cannot be blank")
        String username,

        @NotBlank(message = "Password cannot be blank")
        String password
) {}
