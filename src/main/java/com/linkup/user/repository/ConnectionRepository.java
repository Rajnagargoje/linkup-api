package com.linkup.user.repository;

import com.linkup.user.entity.Connection;
import com.linkup.user.entity.User;
import com.linkup.user.utils.ConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectionRepository
        extends JpaRepository<Connection, Long> {

    Optional<Connection> findByPairKey(String pairKey);

    /*
     * Incoming requests.
     *
     * Example:
     * Rahul -> Ganesh
     *
     * Ganesh will get Rahul here.
     */
    List<Connection> findByReceiverAndStatusOrderByCreatedAtDesc(
            User receiver,
            ConnectionStatus status
    );

    /*
     * Requests sent by current user.
     */
    List<Connection> findBySenderAndStatusOrderByCreatedAtDesc(
            User sender,
            ConnectionStatus status
    );

    /*
     * Get all accepted connections for a user.
     */
    List<Connection> findByStatusAndSenderOrStatusAndReceiver(
            ConnectionStatus senderStatus,
            User sender,
            ConnectionStatus receiverStatus,
            User receiver
    );

    /*
     * Returns the internal User IDs of everyone who is already
     * connected with the current user.
     *
     * This is used by Nearby People.
     */
    @Query("""
            SELECT
                CASE
                    WHEN c.sender.id = :userId THEN c.receiver.id
                    ELSE c.sender.id
                END
            FROM Connection c
            WHERE c.status = :status
              AND (c.sender.id = :userId OR c.receiver.id = :userId)
            """)
    List<Long> findConnectedUserIds(
            @Param("userId") Long userId,
            @Param("status") ConnectionStatus status
    );

    /*
     * Check whether two users are already connected.
     */
    @Query("""
            SELECT COUNT(c) > 0
            FROM Connection c
            WHERE c.status = :status
              AND (
                    (c.sender.id = :user1Id AND c.receiver.id = :user2Id)
                 OR (c.sender.id = :user2Id AND c.receiver.id = :user1Id)
              )
            """)
    boolean areConnected(
            @Param("user1Id") Long user1Id,
            @Param("user2Id") Long user2Id,
            @Param("status") ConnectionStatus status
    );
}