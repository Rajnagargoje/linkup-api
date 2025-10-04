package com.linkup.user.mapper;

import com.linkup.user.dto.request.RegisterUserDTO;
import com.linkup.user.dto.response.UserDTO;
import com.linkup.user.entity.User;

import com.linkup.user.utils.Role;
import com.linkup.user.utils.Status;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


import java.time.LocalDateTime;

@Mapper(componentModel = "spring", imports = {LocalDateTime.class, Status.class, Role.class})
public interface UserMapper {

    UserDTO toUserDTO(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(Status.OFFLINE)")
    @Mapping(target = "role", expression = "java(Role.USER)")
    @Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
    User toEntity(RegisterUserDTO dto);
}
