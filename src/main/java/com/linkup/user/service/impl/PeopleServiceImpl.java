package com.linkup.user.service.impl;


import com.linkup.user.dto.response.PersonProfileResponse;
import com.linkup.user.entity.Connection;
import com.linkup.user.entity.User;
import com.linkup.user.repository.ConnectionRepository;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.PeopleService;
import com.linkup.user.utils.ConnectionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PeopleServiceImpl implements PeopleService {

    private final UserRepository userRepository;
    private final ConnectionRepository connectionRepository;

    @Override
    public PersonProfileResponse getPersonProfile(
            String currentUsername,
            String personId
    ) {

        User currentUser =
                userRepository
                        .findByUsername(currentUsername)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Current user not found"
                                )
                        );

        User user =
                userRepository
                        .findByPublicId(personId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        if (currentUser.getId()
                .equals(user.getId())) {

            throw new IllegalArgumentException(
                    "You cannot view your own profile through People"
            );
        }

        String connectionStatus =
                getConnectionStatus(
                        currentUser,
                        user
                );

        PersonProfileResponse response =
                new PersonProfileResponse();

        response.setId(user.getPublicId());
        response.setUsername(user.getUsername());

        response.setAge(user.getAge());

        response.setGender(
                user.getGender() != null
                        ? user.getGender().toString()
                        : null
        );

        response.setBio(user.getBio());

        response.setProfilePhoto(
                user.getProfilePhoto()
        );

        /*
         * Friend-only profile data.
         *
         * Friends get full photos/interests.
         */
        if ("CONNECTED".equals(connectionStatus)) {

            response.setPhotos(
                    user.getPhotos()
            );

            response.setInterests(
                    user.getInterests()
            );

        } else {

            response.setPhotos(
                    java.util.Collections.emptyList()
            );

            response.setInterests(
                    java.util.Collections.emptyList()
            );
        }

        response.setLookingFor(
                user.getLookingFor() != null
                        ? user.getLookingFor().toString()
                        : null
        );

        response.setOnline(
                Boolean.TRUE.equals(user.getOnline())
        );

        response.setVerified(
                Boolean.TRUE.equals(
                        user.getEmailVerified()
                )
        );

        response.setLastSeenAt(
                user.getLastSeenAt() != null
                        ? user.getLastSeenAt().toString()
                        : null
        );

        response.setConnectionStatus(
                connectionStatus
        );

        return response;
    }

    private String getConnectionStatus(
            User currentUser,
            User targetUser
    ) {

        String first =
                currentUser.getPublicId();

        String second =
                targetUser.getPublicId();

        String pairKey;

        if (first.compareTo(second) < 0) {
            pairKey = first + ":" + second;
        } else {
            pairKey = second + ":" + first;
        }

        return connectionRepository
                .findByPairKey(pairKey)
                .map(Connection::getStatus)
                .map(status -> {

                    if (status ==
                            ConnectionStatus.ACCEPTED) {

                        return "CONNECTED";
                    }

                    if (status ==
                            ConnectionStatus.PENDING) {

                        return "REQUEST_SENT";
                    }

                    return "NONE";
                })
                .orElse("NONE");
    }
}