package com.linkup.user.repository;

import com.linkup.user.entity.EmailVerificationCode;
import com.linkup.user.utils.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findTopByUsernameAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
            String username, VerificationPurpose purpose
    );
}
