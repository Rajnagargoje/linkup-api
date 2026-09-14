package com.linkup.user.controller;


import com.linkup.user.dto.request.LocationUpdateRequest;
import com.linkup.user.dto.response.NearbyPersonResponse;
import com.linkup.user.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class LocationController {

    private final LocationService locationService;

    public LocationController(
            LocationService locationService
    ) {
        this.locationService = locationService;
    }

    // {username} in the path is kept for readability in logs/URLs, but
    // it is NOT trusted for authorization — only the authenticated
    // principal decides whose location gets written. Without this check,
    // any logged-in user could overwrite anyone else's coordinates just
    // by changing the path.
    @PostMapping("/users/{username}/location")
    public ResponseEntity<?> updateLocation(
            @PathVariable String username,
            @RequestBody LocationUpdateRequest request,
            Principal principal
    ) {
        if (!principal.getName().equals(username)) {
            return ResponseEntity.status(403).body("You can only update your own location");
        }

        locationService.updateLocation(principal.getName(), request);

        return ResponseEntity.ok("Location updated successfully");
    }

    // Same reasoning — "nearby people" is always relative to whoever is
    // authenticated, never an arbitrary username someone else can pass in.
    @GetMapping("/people/{username}/nearby")
    public ResponseEntity<?> getNearbyPeople(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") double radius,
            Principal principal
    ) {
        if (!principal.getName().equals(username)) {
            return ResponseEntity.status(403).body("You can only query your own nearby list");
        }

        List<NearbyPersonResponse> people =
                locationService.getNearbyPeople(principal.getName(), radius);

        return ResponseEntity.ok(people);
    }
}
