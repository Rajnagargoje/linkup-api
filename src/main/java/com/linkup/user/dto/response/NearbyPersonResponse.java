package com.linkup.user.dto.response;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class NearbyPersonResponse {

    private Long id;

    private String name;

    private Integer age;

    private String profilePhoto;

    private Double distanceKm;

    private Boolean online;

    private Boolean verified;

    private String meta;

    public NearbyPersonResponse() {
    }

    public NearbyPersonResponse(
            Long id,
            String name,
            Integer age,
            String profilePhoto,
            Double distanceKm,
            Boolean online,
            Boolean verified,
            String meta
    ) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.profilePhoto = profilePhoto;
        this.distanceKm = distanceKm;
        this.online = online;
        this.verified = verified;
        this.meta = meta;
    }

}