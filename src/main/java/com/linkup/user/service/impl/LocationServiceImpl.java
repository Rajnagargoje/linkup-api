package com.linkup.user.service.impl;


import com.linkup.user.dto.request.LocationUpdateRequest;
import com.linkup.user.dto.response.NearbyPersonResponse;
import com.linkup.user.entity.User;
import com.linkup.user.repository.ConnectionRepository;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.LocationService;
import com.linkup.user.utils.ConnectionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final UserRepository userRepository;

    private final ConnectionRepository connectionRepository;


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
        user.setGeohash(
                com.linkup.user.utils.GeoHashUtil.encode(request.getLatitude(), request.getLongitude(), 7)
        );

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
        List<Long> connectedUserIds =
                connectionRepository.findConnectedUserIds(
                        currentUser.getId(),
                        ConnectionStatus.ACCEPTED
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
                        .findByLocationVisibleTrueAndLatitudeIsNotNullAndLongitudeIsNotNullAndIsDeletedFalseAndIsBannedFalse();


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

            if(connectedUserIds.contains(user.getId())){
                continue;
            }


            // Calculate distance
            double distance = calculateDistance(
                    currentUser.getLatitude(),
                    currentUser.getLongitude(),

                    user.getLatitude(),
                    user.getLongitude()
            );

            String connectionStatus =
                    getConnectionStatus(
                            currentUser,
                            user
                    );


            // Check radius
            if (distance <= radiusKm) {

                NearbyPersonResponse response = new NearbyPersonResponse(
                        user.getPublicId(),
                        user.getUsername(),
                        user.getAge(),
                        user.getProfilePhoto(),
                        Math.round(distance * 100.0) / 100.0,
                        user.getOnline(),
                        user.getEmailVerified(),
                        null,
                        connectionStatus
                );

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

    private String getConnectionStatus(
            User currentUser,
            User targetUser
    ) {

        String currentPublicId =
                currentUser.getPublicId();

        String targetPublicId =
                targetUser.getPublicId();

        String pairKey;

        if (currentPublicId.compareTo(targetPublicId) < 0) {
            pairKey =
                    currentPublicId + ":" + targetPublicId;
        } else {
            pairKey =
                    targetPublicId + ":" + currentPublicId;
        }

        return connectionRepository
                .findByPairKey(pairKey)
                .map(connection -> {

                    if (connection.getStatus()
                            == ConnectionStatus.ACCEPTED) {

                        return "CONNECTED";
                    }

                    if (connection.getStatus()
                            == ConnectionStatus.PENDING) {

                        if (connection.getSender()
                                .getId()
                                .equals(currentUser.getId())) {

                            return "REQUEST_SENT";
                        }

                        return "REQUEST_RECEIVED";
                    }

                    return "NONE";

                })
                .orElse("NONE");
    }
}