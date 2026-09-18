package com.linkup.user.dto.response;


import com.linkup.user.utils.ConnectionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConnectionResponseDTO {

    private Long connectionId;

    private String userId;

    private String username;

    private String profilePhoto;

    private Integer age;

    private Boolean online;

    private Boolean verified;

    private ConnectionStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime acceptedAt;
}