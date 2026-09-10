package com.linkup.user.service;




import com.linkup.user.dto.request.LocationUpdateRequest;
import com.linkup.user.dto.response.NearbyPersonResponse;
import com.linkup.user.dto.response.NearbyUserResponse;

import java.util.List;

public interface LocationService {

    void updateLocation(String username, LocationUpdateRequest request);

    List<NearbyPersonResponse> getNearbyPeople(String username, double radiusKm);
}