package com.linkup.user.controller;



import com.linkup.user.dto.request.LocationUpdateRequest;
import com.linkup.user.dto.response.NearbyPersonResponse;
import com.linkup.user.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LocationController {

    private final LocationService locationService;

    public LocationController(
            LocationService locationService
    ) {
        this.locationService = locationService;
    }

    @PostMapping("/users/{userId}/location")
    public ResponseEntity<String> updateLocation(
            @PathVariable String userId,
            @RequestBody LocationUpdateRequest request
    ) {

        locationService.updateLocation(
                userId,
                request
        );

        return ResponseEntity.ok(
                "Location updated successfully"
        );
    }

    @GetMapping("/people/{username}/nearby")
    public ResponseEntity<List<NearbyPersonResponse>> getNearbyPeople(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") double radius
    ) {

        List<NearbyPersonResponse> people =
                locationService.getNearbyPeople(
                        username,
                        radius
                );

        return ResponseEntity.ok(people);
    }
}