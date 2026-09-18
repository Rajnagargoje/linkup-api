package com.linkup.user.service.impl;



import com.linkup.user.dto.response.ConnectionResponseDTO;
import com.linkup.user.entity.Connection;
import com.linkup.user.entity.User;
import com.linkup.user.repository.ConnectionRepository;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.ConnectionService;
import com.linkup.user.utils.ConnectionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConnectionServiceImpl implements ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;

    @Override
    public ConnectionResponseDTO sendRequest(
            String username,
            String targetPublicId
    ) {

        User sender = getUserByUsername(username);

        User receiver = userRepository
                .findByPublicId(targetPublicId)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        /*
         * Cannot send request to yourself.
         */
        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException(
                    "You cannot send a connection request to yourself"
            );
        }

        /*
         * Don't allow deleted/banned/inactive users.
         */
        if (Boolean.TRUE.equals(receiver.getIsDeleted())
                || Boolean.TRUE.equals(receiver.getIsBanned())
                || !Boolean.TRUE.equals(receiver.getIsActive())) {

            throw new IllegalArgumentException(
                    "This user is not available"
            );
        }

        String pairKey = createPairKey(
                sender.getPublicId(),
                receiver.getPublicId()
        );

        var existingOptional =
                connectionRepository.findByPairKey(pairKey);

        if (existingOptional.isPresent()) {

            Connection existing = existingOptional.get();

            if (existing.getStatus() == ConnectionStatus.ACCEPTED) {
                throw new IllegalArgumentException(
                        "You are already friends with this user"
                );
            }

            /*
             * Same request already pending.
             */
            if (existing.getStatus() == ConnectionStatus.PENDING) {

                if (existing.getSender().getId().equals(sender.getId())) {
                    throw new IllegalArgumentException(
                            "Connection request already sent"
                    );
                }

                throw new IllegalArgumentException(
                        "This user has already sent you a connection request"
                );
            }

            /*
             * If previously rejected, allow a new request.
             */
            existing.setSender(sender);
            existing.setReceiver(receiver);
            existing.setStatus(ConnectionStatus.PENDING);
            existing.setAcceptedAt(null);
            existing.setUpdatedAt(LocalDateTime.now());

            Connection saved = connectionRepository.save(existing);

            return toDTO(saved, sender);
        }

        Connection connection = new Connection();

        connection.setSender(sender);
        connection.setReceiver(receiver);
        connection.setStatus(ConnectionStatus.PENDING);
        connection.setPairKey(pairKey);

        Connection saved = connectionRepository.save(connection);

        return toDTO(saved, sender);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectionResponseDTO> getReceivedRequests(
            String username
    ) {

        User currentUser = getUserByUsername(username);

        return connectionRepository
                .findByReceiverAndStatusOrderByCreatedAtDesc(
                        currentUser,
                        ConnectionStatus.PENDING
                )
                .stream()
                .map(connection ->
                        toDTO(connection, currentUser)
                )
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectionResponseDTO> getSentRequests(
            String username
    ) {

        User currentUser = getUserByUsername(username);

        return connectionRepository
                .findBySenderAndStatusOrderByCreatedAtDesc(
                        currentUser,
                        ConnectionStatus.PENDING
                )
                .stream()
                .map(connection ->
                        toDTO(connection, currentUser)
                )
                .toList();
    }

    @Override
    public ConnectionResponseDTO acceptRequest(
            String username,
            Long connectionId
    ) {

        User currentUser = getUserByUsername(username);

        Connection connection = connectionRepository
                .findById(connectionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Connection request not found"
                        )
                );

        /*
         * Only receiver can accept.
         */
        if (!connection.getReceiver().getId()
                .equals(currentUser.getId())) {

            throw new IllegalArgumentException(
                    "You can only accept requests sent to you"
            );
        }

        if (connection.getStatus() != ConnectionStatus.PENDING) {

            throw new IllegalArgumentException(
                    "Connection request is no longer pending"
            );
        }

        connection.setStatus(ConnectionStatus.ACCEPTED);
        connection.setAcceptedAt(LocalDateTime.now());

        Connection saved =
                connectionRepository.save(connection);

        return toDTO(saved, currentUser);
    }

    @Override
    public void rejectRequest(
            String username,
            Long connectionId
    ) {

        User currentUser = getUserByUsername(username);

        Connection connection = connectionRepository
                .findById(connectionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Connection request not found"
                        )
                );

        /*
         * Only receiver can reject.
         */
        if (!connection.getReceiver().getId()
                .equals(currentUser.getId())) {

            throw new IllegalArgumentException(
                    "You can only reject requests sent to you"
            );
        }

        if (connection.getStatus() != ConnectionStatus.PENDING) {

            throw new IllegalArgumentException(
                    "Connection request is no longer pending"
            );
        }

        connection.setStatus(ConnectionStatus.REJECTED);

        connectionRepository.save(connection);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectionResponseDTO> getFriends(
            String username
    ) {

        User currentUser = getUserByUsername(username);

        return connectionRepository
                .findByStatusAndSenderOrStatusAndReceiver(
                        ConnectionStatus.ACCEPTED,
                        currentUser,
                        ConnectionStatus.ACCEPTED,
                        currentUser
                )
                .stream()
                .map(connection ->
                        toDTO(connection, currentUser)
                )
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String getConnectionStatus(
            String username,
            String targetPublicId
    ) {

        User currentUser = getUserByUsername(username);

        User target = userRepository
                .findByPublicId(targetPublicId)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        if (currentUser.getId().equals(target.getId())) {
            return "SELF";
        }

        String pairKey = createPairKey(
                currentUser.getPublicId(),
                target.getPublicId()
        );

        return connectionRepository
                .findByPairKey(pairKey)
                .map(connection -> {

                    if (connection.getStatus()
                            == ConnectionStatus.ACCEPTED) {

                        return "CONNECTED";
                    }

                    if (connection.getStatus()
                            == ConnectionStatus.PENDING) {

                        if (connection.getSender()
                                .getId()
                                .equals(currentUser.getId())) {

                            return "REQUEST_SENT";
                        }

                        return "REQUEST_RECEIVED";
                    }

                    return "NONE";

                })
                .orElse("NONE");
    }


    @Override
    public void unfriend(
            String username,
            String targetPublicId
    ) {

        User currentUser = getUserByUsername(username);

        User target = userRepository
                .findByPublicId(targetPublicId)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        String pairKey = createPairKey(
                currentUser.getPublicId(),
                target.getPublicId()
        );

        Connection connection =
                connectionRepository
                        .findByPairKey(pairKey)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Connection not found"
                                )
                        );

        if (connection.getStatus()
                != ConnectionStatus.ACCEPTED) {

            throw new IllegalArgumentException(
                    "You are not friends with this user"
            );
        }

        /*
         * For now we delete the friendship.
         *
         * Later we can change this to BLOCKED / UNFRIENDED
         * if you want friendship history.
         */
        connectionRepository.delete(connection);
    }


    private User getUserByUsername(String username) {

        return userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );
    }

    private String createPairKey(
            String publicId1,
            String publicId2
    ) {

        if (publicId1.compareTo(publicId2) < 0) {
            return publicId1 + ":" + publicId2;
        }

        return publicId2 + ":" + publicId1;
    }

    private ConnectionResponseDTO toDTO(
            Connection connection,
            User currentUser
    ) {

        User otherUser;

        if (connection.getSender()
                .getId()
                .equals(currentUser.getId())) {

            otherUser = connection.getReceiver();

        } else {

            otherUser = connection.getSender();
        }

        return new ConnectionResponseDTO(
                connection.getId(),
                otherUser.getPublicId(),
                otherUser.getUsername(),
                otherUser.getProfilePhoto(),
                otherUser.getAge(),
                otherUser.getOnline(),
                otherUser.getEmailVerified(),
                connection.getStatus(),
                connection.getCreatedAt(),
                connection.getAcceptedAt()
        );
    }
}