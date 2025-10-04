package com.linkup.user.service.impl;

import com.linkup.user.dto.request.LoginDTO;
import com.linkup.user.dto.request.RegisterUserDTO;
import com.linkup.user.dto.response.UserDTO;
import com.linkup.user.entity.User;
import com.linkup.user.exception.CustomAuthException;
import com.linkup.user.exception.UserAlreadyExistsException;
import com.linkup.user.mapper.UserMapper;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    @Autowired
    private UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;



    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.info("Loading user by username: {}", username);
        Optional<User> userDetails = userRepository.findByUsername(username);
        return userDetails.map(UserPrinciples::new).orElseThrow(()->{
            logger.error("User not found: {}", username);
            return new UsernameNotFoundException("User not found: " + username);
        });

    }
    @Override
    public UserDTO register(RegisterUserDTO dto) {
        logger.info("Adding new user with username: {}",dto.username());

        if(userRepository.existsByUsername(dto.username())){
            logger.info("User already exist with username: {}",dto.username());
            throw new UserAlreadyExistsException("User already exist with username: " + dto.username());

        }
        Optional<User> existingUser = userRepository.findByEmail(dto.email());
        if (existingUser.isPresent()) {
            logger.info("User already exists with email: " + dto.email());
            throw new UserAlreadyExistsException("User already exists with email: " + dto.email());
        }
        User user = userMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.password()));
        return userMapper.toUserDTO(userRepository.save(user));
    }

    @Override
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userMapper.toUserDTO(user);
    }

    @Override
    public String loginAuthenticatedUser(LoginDTO loginRequest) {
        if (loginRequest == null || loginRequest.username() == null || loginRequest.password() == null) {
            throw new IllegalArgumentException("Username and password cannot be null.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            return jwtService.generateToken(loginRequest.username());

        } catch (BadCredentialsException ex) {
            // custom handling for wrong credentials
            throw new CustomAuthException("Bad credentials");
        }
    }




}
