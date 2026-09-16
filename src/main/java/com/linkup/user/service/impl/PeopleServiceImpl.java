package com.linkup.user.service.impl;

import com.linkup.user.dto.response.PersonProfileResponse;
import com.linkup.user.entity.User;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.PeopleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PeopleServiceImpl implements PeopleService {

    private final UserRepository userRepository;

    public PersonProfileResponse getPersonProfile(
            String personId){

        User user = userRepository.findByPublicId(personId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        PersonProfileResponse response = new PersonProfileResponse();

        response.setId(user.getPublicId());
        response.setUsername(user.getUsername());
//        response.setName(user.getName());
        response.setUsername(user.getUsername());
        response.setAge(user.getAge());

        response.setGender(String.valueOf(user.getGender()));
//        response.setNationality(user.getNationality());
        response.setBio(user.getBio());

        response.setProfilePhoto(user.getProfilePhoto());
        response.setPhotos(user.getPhotos());
        response.setInterests(user.getInterests());

        response.setLookingFor(String.valueOf(user.getLookingFor()));
//        response.setRelationshipStatus(
//                user.getRelationshipStatus()
//        );

        response.setOnline(user.getOnline());
        response.setVerified(user.getEmailVerified());

        response.setLastSeenAt(String.valueOf(user.getLastSeenAt()));

        return response;
    }
}
