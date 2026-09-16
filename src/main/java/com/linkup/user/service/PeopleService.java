package com.linkup.user.service;

import com.linkup.user.dto.response.PersonProfileResponse;

public interface PeopleService {
    PersonProfileResponse getPersonProfile(
            String personId);
}
