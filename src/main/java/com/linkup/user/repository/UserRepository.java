package com.linkup.user.repository;

import com.linkup.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    org.springframework.data.domain.Page<User> findByIsActiveTrueAndIsBannedFalseAndIsDeletedFalse(org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByPublicId(String publicId);

    List<User> findByLocationVisibleTrueAndLatitudeIsNotNullAndLongitudeIsNotNullAndIsDeletedFalseAndIsBannedFalse();
    interface NearbyRow {
        String getPublicId();
        String getName();
        Integer getAge();
        String getProfilePhoto();
        Double getDistanceKm();
        Boolean getOnline();
        Boolean getVerified();
        String getConnectionStatus();
    }

    @Query(value = """
    WITH nearby AS (
        SELECT u.id, u.public_id, u.username, u.age, u.profile_photo,
               u.online, u.email_verified,
               12742.0 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
                   POWER(SIN(RADIANS(u.latitude - :latitude) / 2.0), 2)
                   + COS(RADIANS(:latitude)) * COS(RADIANS(u.latitude))
                   * POWER(SIN(RADIANS(u.longitude - :longitude) / 2.0), 2)
               )))) AS distance_km
        FROM users u
        WHERE u.id <> :userId
          AND u.location_visible = TRUE
          AND u.is_active = TRUE
          AND u.is_deleted = FALSE
          AND u.is_banned = FALSE
          AND u.latitude BETWEEN :minLatitude AND :maxLatitude
          AND u.longitude IS NOT NULL
    )
    SELECT n.public_id AS "publicId", n.username AS "name",
           n.age AS "age", n.profile_photo AS "profilePhoto",
           n.distance_km AS "distanceKm", n.online AS "online",
           n.email_verified AS "verified",
           CASE
               WHEN c.status = 'PENDING' AND c.sender_id = :userId
                   THEN 'REQUEST_SENT'
               WHEN c.status = 'PENDING' THEN 'REQUEST_RECEIVED'
               ELSE 'NONE'
           END AS "connectionStatus"
    FROM nearby n
    LEFT JOIN connections c
      ON (c.sender_id = :userId AND c.receiver_id = n.id)
      OR (c.receiver_id = :userId AND c.sender_id = n.id)
    WHERE n.distance_km <= :radiusKm
      AND (c.id IS NULL OR c.status <> 'ACCEPTED')
      AND NOT EXISTS (
          SELECT 1 FROM chat_blocks b
          WHERE (b.blocker = :identity AND b.target = ('u:' || n.public_id))
             OR (b.target = :identity AND b.blocker = ('u:' || n.public_id))
      )
      AND NOT EXISTS (
          SELECT 1 FROM chat_reports r
          WHERE r.reporter = :identity AND r.target = ('u:' || n.public_id)
      )
    ORDER BY n.distance_km, n.id
    """, nativeQuery = true)
    List<NearbyRow> findNearbyPeople(
            @Param("userId") Long userId,
            @Param("identity") String identity,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("radiusKm") double radiusKm
    );
}
