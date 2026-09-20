package com.linkup.user.repository;

import com.linkup.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
