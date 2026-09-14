package com.linkup.user.dto.request;


import jakarta.validation.constraints.NotBlank;

public record LoginDTO(

        // Despite the field name, the backend now accepts EITHER the
        // actual username OR the account's email here (see
        // UserServiceImpl.loginAuthenticatedUser) — kept named
        // "username" so the existing frontend request body shape
        // ({"username": "...", "password": "..."}) doesn't need to change.
        @NotBlank(message = "Username cannot be blank")
        String username,

        @NotBlank(message = "Password cannot be blank")
        String password
) {}
