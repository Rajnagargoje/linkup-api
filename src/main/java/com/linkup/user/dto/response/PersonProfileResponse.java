package com.linkup.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PersonProfileResponse {

    private String id;
    private String username;
    private String name;

    private Integer age;
    private String gender;
//    private String nationality;

    private String bio;

    private String profilePhoto;
    private List<String> photos;

    private List<String> interests;

    private String lookingFor;
//    private String relationshipStatus;

    private boolean online;
    private boolean verified;

    private String lastSeenAt;
    private String connectionStatus;


}