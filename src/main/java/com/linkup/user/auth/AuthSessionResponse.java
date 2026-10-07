package com.linkup.user.auth;

import com.linkup.user.dto.response.UserDTO;
import java.time.Instant;

public record AuthSessionResponse(String token, String refreshToken, Instant refreshExpiresAt, UserDTO user) {}
