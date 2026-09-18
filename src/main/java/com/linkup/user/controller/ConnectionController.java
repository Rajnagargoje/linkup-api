package com.linkup.user.controller;


import com.linkup.user.dto.response.ApiResponseDTO;
import com.linkup.user.dto.response.ConnectionResponseDTO;
import com.linkup.user.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    /*
     * Send connection request.
     *
     * POST /api/connections/request/{publicId}
     */
    @PostMapping("/request/{publicId}")
    public ResponseEntity<ApiResponseDTO<ConnectionResponseDTO>>
    sendRequest(
            @PathVariable String publicId,
            Principal principal
    ) {

        ConnectionResponseDTO response =
                connectionService.sendRequest(
                        principal.getName(),
                        publicId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        new ApiResponseDTO<>(
                                HttpStatus.CREATED.value(),
                                "Connection request sent",
                                response
                        )
                );
    }

    /*
     * Incoming requests.
     *
     * GET /api/connections/requests
     */
    @GetMapping("/requests")
    public ResponseEntity<ApiResponseDTO<List<ConnectionResponseDTO>>>
    getReceivedRequests(
            Principal principal
    ) {

        List<ConnectionResponseDTO> requests =
                connectionService.getReceivedRequests(
                        principal.getName()
                );

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        200,
                        "Connection requests fetched",
                        requests
                )
        );
    }

    /*
     * Requests sent by current user.
     */
    @GetMapping("/sent")
    public ResponseEntity<ApiResponseDTO<List<ConnectionResponseDTO>>>
    getSentRequests(
            Principal principal
    ) {

        List<ConnectionResponseDTO> requests =
                connectionService.getSentRequests(
                        principal.getName()
                );

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        200,
                        "Sent requests fetched",
                        requests
                )
        );
    }

    /*
     * Accept request.
     *
     * POST /api/connections/{connectionId}/accept
     */
    @PostMapping("/{connectionId}/accept")
    public ResponseEntity<ApiResponseDTO<ConnectionResponseDTO>>
    acceptRequest(
            @PathVariable Long connectionId,
            Principal principal
    ) {

        ConnectionResponseDTO response =
                connectionService.acceptRequest(
                        principal.getName(),
                        connectionId
                );

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        200,
                        "Connection request accepted",
                        response
                )
        );
    }

    /*
     * Reject request.
     */
    @PostMapping("/{connectionId}/reject")
    public ResponseEntity<ApiResponseDTO<Void>>
    rejectRequest(
            @PathVariable Long connectionId,
            Principal principal
    ) {

        connectionService.rejectRequest(
                principal.getName(),
                connectionId
        );

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        200,
                        "Connection request rejected",
                        null
                )
        );
    }

    /*
     * Friends list.
     */
    @GetMapping("/friends")
    public ResponseEntity<ApiResponseDTO<List<ConnectionResponseDTO>>>
    getFriends(
            Principal principal
    ) {

        List<ConnectionResponseDTO> friends =
                connectionService.getFriends(
                        principal.getName()
                );

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        200,
                        "Friends fetched",
                        friends
                )
        );
    }

    /*
     * Get relationship with another user.
     */
    @GetMapping("/status/{publicId}")
    public ResponseEntity<ApiResponseDTO<String>>
    getConnectionStatus(
            @PathVariable String publicId,
            Principal principal
    ) {

        String status =
                connectionService.getConnectionStatus(
                        principal.getName(),
                        publicId
                );

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        200,
                        "Connection status fetched",
                        status
                )
        );
    }

    /*
     * Unfriend.
     */
    @DeleteMapping("/{publicId}")
    public ResponseEntity<ApiResponseDTO<Void>>
    unfriend(
            @PathVariable String publicId,
            Principal principal
    ) {

        connectionService.unfriend(
                principal.getName(),
                publicId
        );

        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        200,
                        "Friend removed",
                        null
                )
        );
    }
}