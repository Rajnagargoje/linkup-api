package com.linkup.user.service;

import com.linkup.user.dto.request.LoginDTO;
import com.linkup.user.dto.request.RegisterUserDTO;
import com.linkup.user.dto.response.UserDTO;
import jakarta.validation.Valid;

public interface UserService {
    UserDTO register(RegisterUserDTO dto);
    UserDTO getUserById(Long id);

    String loginAuthenticatedUser(@Valid LoginDTO dto);
}

