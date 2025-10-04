package com.linkup.user.dto.response;



import com.linkup.user.utils.Role;
import com.linkup.user.utils.Status;

import java.time.LocalDateTime;

public record UserDTO(
        Long id,
        String username,
        String email,
        Role role,
        Status status,
        LocalDateTime createdAt
) {}

