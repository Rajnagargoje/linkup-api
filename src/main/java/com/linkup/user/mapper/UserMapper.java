package com.linkup.user.mapper;

import com.linkup.user.dto.request.RegisterUserDTO;
import com.linkup.user.dto.response.UserDTO;
import com.linkup.user.entity.User;

import com.linkup.user.utils.Role;
import com.linkup.user.utils.Status;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring", imports = {Status.class, Role.class})
public interface UserMapper {

    // NOTE: only ever map fields here that are safe to hand back to a
    // client. Do NOT add password, tokenVersion, failedLoginAttempts,
    // lockedUntil, fcmToken, or exact lat/long here without checking
    // locationVisible first (see UserServiceImpl.toSafeUserDTO).
    UserDTO toUserDTO(User user);

    // createdAt/updatedAt/publicId are set by User's own @PrePersist —
    // leave them alone here so there's exactly one place that sets them.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(Status.OFFLINE)")
    @Mapping(target = "role", expression = "java(Role.USER)")
    User toEntity(RegisterUserDTO dto);
}
