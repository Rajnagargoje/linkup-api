package com.linkup.user.dto.response;

/**
 * Only /register uses this shape. /login still returns a bare token
 * string (data: "<jwt>") — no reason to change a contract the frontend
 * already relies on when only one endpoint actually needed to change.
 */
public record AuthResponseDTO(
        UserDTO user,
        String token
) {}
