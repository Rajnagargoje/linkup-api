package com.linkup.user.service;

import com.linkup.user.dto.request.LoginDTO;
import com.linkup.user.dto.request.RegisterUserDTO;
import com.linkup.user.dto.request.UpdateProfileDTO;
import com.linkup.user.dto.response.AuthResponseDTO;
import com.linkup.user.dto.response.UserDTO;
import com.linkup.user.dto.response.UsernameAvailabilityResponse;
import com.linkup.user.utils.Status;
import jakarta.validation.Valid;

public interface UserService {
    AuthResponseDTO register(RegisterUserDTO dto);

    UsernameAvailabilityResponse checkUsernameAvailability(String username);

    UserDTO getMe(String username);

    String loginAuthenticatedUser(@Valid LoginDTO dto);

    UserDTO updateProfile(String username, UpdateProfileDTO dto);

    void logout(String username);

    void deleteAccount(String username);

    void updateStatus(String username, Status status);
}
