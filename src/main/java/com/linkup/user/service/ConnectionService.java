package com.linkup.user.service;



import com.linkup.user.dto.response.ConnectionResponseDTO;

import java.util.List;

public interface ConnectionService {

    ConnectionResponseDTO sendRequest(
            String username,
            String targetPublicId
    );

    List<ConnectionResponseDTO> getReceivedRequests(
            String username
    );

    List<ConnectionResponseDTO> getSentRequests(
            String username
    );

    ConnectionResponseDTO acceptRequest(
            String username,
            Long connectionId
    );

    void rejectRequest(
            String username,
            Long connectionId
    );

    List<ConnectionResponseDTO> getFriends(
            String username
    );

    String getConnectionStatus(
            String username,
            String targetPublicId
    );

    void unfriend(
            String username,
            String targetPublicId
    );
}
