package com.linkup.user.service.impl;

import com.linkup.user.dto.request.LoginDTO;
import com.linkup.user.dto.request.RegisterUserDTO;
import com.linkup.user.dto.request.UpdateProfileDTO;
import com.linkup.user.dto.response.AuthResponseDTO;
import com.linkup.user.dto.response.UserDTO;
import com.linkup.user.dto.response.UsernameAvailabilityResponse;
import com.linkup.user.entity.User;
import com.linkup.user.exception.CustomAuthException;
import com.linkup.user.exception.ResourceNotFoundException;
import com.linkup.user.exception.UserAlreadyExistsException;
import com.linkup.user.mapper.UserMapper;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.UserService;
import com.linkup.user.utils.Status;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    @Autowired
    private UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> userDetails = userRepository.findByUsername(username);
        return userDetails.map(UserPrinciples::new).orElseThrow(() -> {
            logger.warn("User not found: {}", username);
            return new UsernameNotFoundException("User not found: " + username);
        });
    }

    @Override
    public AuthResponseDTO register(RegisterUserDTO dto) {
        logger.info("Registering new user with username: {}", dto.username());

        if (userRepository.existsByUsername(dto.username())) {
            throw new UserAlreadyExistsException("User already exists with username: " + dto.username());
        }
        if (userRepository.existsByEmail(dto.email())) {
            throw new UserAlreadyExistsException("User already exists with email: " + dto.email());
        }

        User user = userMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.password()));
        User saved = userRepository.save(user);

        // Register now returns a token immediately (like login does)
        // instead of forcing a separate login call right after signup —
        // onboarding, email verification, and photo upload all need an
        // authenticated session, and there's no reason to make the
        // frontend do that round trip itself when we already have
        // everything we need to issue the token right here.
        String token = jwtService.generateToken(saved.getUsername(), saved.getTokenVersion());

        return new AuthResponseDTO(userMapper.toUserDTO(saved), token);
    }

    @Override
    public UsernameAvailabilityResponse checkUsernameAvailability(String username) {
        String normalized = username == null ? "" : username.trim();

        if (normalized.length() < 4 || normalized.length() > 20) {
            // Don't bother hitting the DB for something that can never
            // pass registration's own validation anyway.
            return new UsernameAvailabilityResponse(false, java.util.List.of());
        }

        if (!userRepository.existsByUsername(normalized)) {
            return new UsernameAvailabilityResponse(true, java.util.List.of());
        }

        // Taken — generate suggestions and verify each against the DB
        // ourselves rather than having the frontend guess-and-check one
        // at a time. Cap attempts so a pathological base name (e.g. one
        // where every numbered variant happens to be taken) can't loop
        // forever.
        java.util.List<String> suggestions = new java.util.ArrayList<>();
        java.util.Random random = new java.util.Random();
        int attempts = 0;

        String base = normalized.length() > 15 ? normalized.substring(0, 15) : normalized;

        while (suggestions.size() < 3 && attempts < 20) {
            attempts++;
            String candidate = switch (attempts % 3) {
                case 0 -> base + "_" + (1000 + random.nextInt(9000));
                case 1 -> base + (10 + random.nextInt(90));
                default -> base + (100 + random.nextInt(900));
            };
            if (candidate.length() <= 20 && !userRepository.existsByUsername(candidate)
                    && !suggestions.contains(candidate)) {
                suggestions.add(candidate);
            }
        }

        return new UsernameAvailabilityResponse(false, suggestions);
    }

    @Override
    public UserDTO getMe(String username) {
        User user = findActiveUserOrThrow(username);
        return userMapper.toUserDTO(user);
    }

    @Override
    @Transactional
    public String loginAuthenticatedUser(LoginDTO loginRequest) {
        if (loginRequest == null || loginRequest.username() == null || loginRequest.password() == null) {
            throw new IllegalArgumentException("Username and password cannot be null.");
        }

        // Accept either a username or an email in the same field — resolve
        // it to the real username up front since Spring Security's
        // UserDetailsService (loadUserByUsername) only knows usernames.
        String identifier = loginRequest.username().trim();
        Optional<User> userOpt = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier));

        if (userOpt.isEmpty()) {
            // Same message as bad-password below — don't reveal whether
            // the identifier exists at all.
            throw new CustomAuthException("Invalid username/email or password");
        }

        User user = userOpt.get();

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new CustomAuthException("Account temporarily locked due to failed login attempts. Try again later.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), loginRequest.password())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Successful login — clear any failed-attempt counter and stamp lastLoginAt.
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            return jwtService.generateToken(user.getUsername(), user.getTokenVersion());

        } catch (BadCredentialsException ex) {
            registerFailedAttempt(user);
            throw new CustomAuthException("Invalid username/email or password");
        } catch (DisabledException ex) {
            // isEnabled() = active && !banned && !deleted — covers all three cases.
            throw new CustomAuthException("This account has been banned, deleted, or deactivated");
        } catch (LockedException ex) {
            // isAccountNonLocked() only reflects the failed-attempt lockout below;
            // in practice the manual lockedUntil check above already short-circuits
            // this, but keep it accurate in case that ever changes.
            throw new CustomAuthException("Account temporarily locked due to failed login attempts. Try again later.");
        }
    }

    @Override
    @Transactional
    public UserDTO updateProfile(String username, UpdateProfileDTO dto) {
        User user = findActiveUserOrThrow(username);

        if (dto.dob() != null) {
            user.setDob(dto.dob());
            // Recomputed at write time rather than on every read — a
            // once-a-year drift (before someone's next birthday) is an
            // accepted tradeoff for not doing this calculation on every
            // profile fetch.
            user.setAge(Period.between(dto.dob(), java.time.LocalDate.now()).getYears());
        }
        if (dto.gender() != null) user.setGender(dto.gender());
        if (dto.bio() != null) user.setBio(dto.bio());
        if (dto.profilePhoto() != null) user.setProfilePhoto(dto.profilePhoto());
        if (dto.photos() != null) user.setPhotos(dto.photos());
        if (dto.interests() != null) user.setInterests(dto.interests());
        if (dto.lookingFor() != null) user.setLookingFor(dto.lookingFor());
        if (dto.genderPreference() != null) user.setGenderPreference(dto.genderPreference());
        if (dto.minAgePreference() != null) user.setMinAgePreference(dto.minAgePreference());
        if (dto.maxAgePreference() != null) user.setMaxAgePreference(dto.maxAgePreference());
        if (dto.maxDistanceKm() != null) user.setMaxDistanceKm(dto.maxDistanceKm());

        user.setOnboardingCompleted(true);

        return userMapper.toUserDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public void logout(String username) {
        // JWT is stateless, so the client dropping the token is what
        // actually ends the session — this just flips presence off
        // server-side immediately instead of waiting for the WebSocket
        // disconnect event (belt-and-suspenders for REST-only clients).
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setOnline(false);
            user.setStatus(Status.OFFLINE);
            user.setLastSeenAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    @Override
    @Transactional
    public void deleteAccount(String username) {
        User user = findActiveUserOrThrow(username);

        // Soft delete: Room.messages (Mongo) reference the sender by
        // username, so hard-deleting the row would either orphan that
        // history or require a cross-store cascade. Instead we anonymize
        // and free up the original username/email for reuse, and bump
        // tokenVersion so every outstanding JWT for this account is
        // rejected on its very next request — not just at natural expiry.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user.setUsername("deleted_" + user.getId() + "_" + suffix);
        user.setEmail("deleted_" + user.getId() + "_" + suffix + "@linkup.invalid");
        user.setIsDeleted(true);
        user.setIsActive(false);
        user.setDeletedAt(LocalDateTime.now());
        user.setOnline(false);
        user.setStatus(Status.OFFLINE);
        user.setBio(null);
        user.setProfilePhoto(null);
        user.setPhotos(new java.util.ArrayList<>());
        user.setLatitude(null);
        user.setLongitude(null);
        user.setLocationVisible(false);
        user.setFcmToken(null);
        user.setTokenVersion(user.getTokenVersion() + 1);

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateStatus(String username, Status status) {
        User user = findActiveUserOrThrow(username);
        user.setStatus(status);
        if (status == Status.OFFLINE) {
            user.setOnline(false);
            user.setLastSeenAt(LocalDateTime.now());
        } else {
            user.setOnline(true);
        }
        userRepository.save(user);
    }

    private User findActiveUserOrThrow(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new ResourceNotFoundException("User not found: " + username);
        }
        return user;
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
        attempts++;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }
        userRepository.save(user);
    }
}
