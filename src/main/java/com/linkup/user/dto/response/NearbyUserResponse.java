package com.linkup.user.dto.response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NearbyUserResponse {

    private Long id;

    private String username;

    private Double distanceKm;
}

