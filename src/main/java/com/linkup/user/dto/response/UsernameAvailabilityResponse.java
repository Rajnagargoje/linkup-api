package com.linkup.user.dto.response;

import java.util.List;

public record UsernameAvailabilityResponse(
        boolean available,
        List<String> suggestions
) {}
