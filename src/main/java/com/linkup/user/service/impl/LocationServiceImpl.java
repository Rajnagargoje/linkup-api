package com.linkup.user.service.impl;


import com.linkup.user.dto.request.LocationUpdateRequest;
import com.linkup.user.dto.response.NearbyPersonResponse;
import com.linkup.user.entity.User;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final UserRepository userRepository;


    // ============================================================
    // UPDATE CURRENT USER LOCATION
    // ============================================================

    @Override
    public void updateLocation(
            String username,
            LocationUpdateRequest request
    ) {

        // Validate latitude
        if (request.getLatitude() == null ||
                request.getLatitude() < -90 ||
                request.getLatitude() > 90) {

            throw new IllegalArgumentException(
                    "Invalid latitude"
            );
        }

        // Validate longitude
        if (request.getLongitude() == null ||
                request.getLongitude() < -180 ||
                request.getLongitude() > 180) {

            throw new IllegalArgumentException(
                    "Invalid longitude"
            );
        }

        // Find logged-in user
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        // Save location
        user.setLatitude(request.getLatitude());
        user.setLongitude(request.getLongitude());

        // Save update time
        user.setLocationUpdatedAt(
                LocalDateTime.now()
        );

        // Save user
        userRepository.save(user);
    }


    // ============================================================
    // GET NEARBY PEOPLE
    // ============================================================



    @Override
    public List<NearbyPersonResponse> getNearbyPeople(
            String username,
            double radiusKm
    ) {

        // Validate radius
        if (radiusKm <= 0) {
            throw new IllegalArgumentException(
                    "Radius must be greater than 0"
            );
        }

        // Find current user
        User currentUser = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );


        // Check current user's location
        if (currentUser.getLatitude() == null ||
                currentUser.getLongitude() == null) {

            throw new RuntimeException(
                    "Your location is not available. " +
                            "Please update your location first."
            );
        }


        // Get users who have enabled location visibility
        List<User> users =
                userRepository
                        .findByLocationVisibleTrueAndLatitudeIsNotNullAndLongitudeIsNotNull();


        List<NearbyPersonResponse> nearbyPeople =
                new ArrayList<>();


        // ========================================================
        // CALCULATE DISTANCE FOR EACH USER
        // ========================================================

        for (User user : users) {

            // Don't show current user
            if (user.getId().equals(currentUser.getId())) {
                continue;
            }


            // Calculate distance
            double distance = calculateDistance(
                    currentUser.getLatitude(),
                    currentUser.getLongitude(),

                    user.getLatitude(),
                    user.getLongitude()
            );


            // Check radius
            if (distance <= radiusKm) {

                NearbyPersonResponse response =
                        new NearbyPersonResponse();


                // Basic user information
                response.setId(user.getId());

                /*
                 * Your current User entity contains username,
                 * but does not contain name.
                 *
                 * So for now we use username as name.
                 *
                 * Later, if you have a Profile entity,
                 * we will get the real name from Profile.
                 */
                response.setName(
                        user.getUsername()
                );


                // Distance
                response.setDistanceKm(
                        Math.round(distance * 100.0) / 100.0
                );


                /*
                 * These fields are currently not available
                 * in your User entity.
                 *
                 * They will remain null until we connect
                 * your Profile entity.
                 */
                response.setAge(null);
                response.setProfilePhoto(null);
                response.setOnline(null);
                response.setVerified(null);
                response.setMeta(null);


                // Add to result
                nearbyPeople.add(response);
            }
        }


        // ========================================================
        // SORT BY DISTANCE
        // ========================================================

        nearbyPeople.sort(
                (person1, person2) ->
                        Double.compare(
                                person1.getDistanceKm(),
                                person2.getDistanceKm()
                        )
        );


        return nearbyPeople;
    }


    // ============================================================
    // HAVERSINE DISTANCE CALCULATION
    // ============================================================

    private double calculateDistance(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        /*
         * Earth's average radius in kilometers.
         */
        final double EARTH_RADIUS_KM = 6371.0;


        // Difference between latitudes
        double latitudeDistance =
                Math.toRadians(
                        latitude2 - latitude1
                );


        // Difference between longitudes
        double longitudeDistance =
                Math.toRadians(
                        longitude2 - longitude1
                );


        // Haversine formula
        double a =
                Math.sin(latitudeDistance / 2)
                        * Math.sin(latitudeDistance / 2)

                        +

                        Math.cos(
                                Math.toRadians(latitude1)
                        )
                                *
                                Math.cos(
                                        Math.toRadians(latitude2)
                                )
                                *
                                Math.sin(longitudeDistance / 2)
                                *
                                Math.sin(longitudeDistance / 2);


        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );


        // Distance in kilometers
        return EARTH_RADIUS_KM * c;
    }
}