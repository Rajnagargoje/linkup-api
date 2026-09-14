package com.linkup.user.repository;

import com.linkup.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByPublicId(String publicId);

    // Nearby search must exclude deleted/banned users — a deleted account
    // shouldn't keep showing up to strangers just because its row is
    // still physically in the table (soft delete).
    List<User> findByLocationVisibleTrueAndLatitudeIsNotNullAndLongitudeIsNotNullAndIsDeletedFalseAndIsBannedFalse();
}
