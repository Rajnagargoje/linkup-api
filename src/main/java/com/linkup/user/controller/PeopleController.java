package com.linkup.user.controller;


import com.linkup.user.dto.response.PersonProfileResponse;
import com.linkup.user.service.PeopleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/people")
@RequiredArgsConstructor
public class PeopleController {
    private final PeopleService peopleService;

    @GetMapping("/{personId}")
    public ResponseEntity<PersonProfileResponse> getPersonProfile (@PathVariable String personId){

        PersonProfileResponse response = peopleService.getPersonProfile(personId);
        return ResponseEntity.ok(response);

    }
}
