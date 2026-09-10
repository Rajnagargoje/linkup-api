package com.linkup.user.repository;

import com.linkup.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    // Find users who have location sharing enabled
    // and have valid latitude/longitude values
    List<User> findByLocationVisibleTrueAndLatitudeIsNotNullAndLongitudeIsNotNull();
}

