package com.linkup.user.dto.response;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class NearbyPersonResponse {

    private String publicId;

    private String name;

    private Integer age;

    private String profilePhoto;

    private Double distanceKm;

    private Boolean online;

    // Currently just mirrors User.emailVerified — once phone verification
    // exists too, decide whether "verified" here should mean "either" or
    // "both" rather than adding a second boolean to this DTO.
    private Boolean verified;

    private String meta;

    private String connectionStatus;

    public NearbyPersonResponse() {
    }

    public NearbyPersonResponse(
            String publicId,
            String name,
            Integer age,
            String profilePhoto,
            Double distanceKm,
            Boolean online,
            Boolean verified,
            String meta,
            String connectionStatus
    ) {
        this.publicId = publicId;
        this.name = name;
        this.age = age;
        this.profilePhoto = profilePhoto;
        this.distanceKm = distanceKm;
        this.online = online;
        this.verified = verified;
        this.meta = meta;
        this.connectionStatus = connectionStatus;
    }

}