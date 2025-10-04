package com.linkup.user.controller;


import com.linkup.user.dto.request.LoginDTO;
import com.linkup.user.dto.request.RegisterUserDTO;
import com.linkup.user.dto.response.ApiResponseDTO;
import com.linkup.user.dto.response.UserDTO;
import com.linkup.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);


    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponseDTO<UserDTO>> register(@Valid @RequestBody RegisterUserDTO dto) {
        logger.info("Received request to register user: {} ", dto.username());
        return new ResponseEntity<>(new ApiResponseDTO<>(HttpStatus.CREATED.value(), "User registered successfully", userService.register(dto)), HttpStatus.CREATED);
    }
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<String>> login(@Valid @RequestBody LoginDTO authRequest) {
        logger.info("Received login request for user: {}", authRequest.username());
        String token = userService.loginAuthenticatedUser(authRequest);
        logger.info("Login successful for user: {}", authRequest.username());
        ApiResponseDTO<String> response = new ApiResponseDTO<>(HttpStatus.OK.value(), "Login successful", token);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }



    @GetMapping("takeid/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
