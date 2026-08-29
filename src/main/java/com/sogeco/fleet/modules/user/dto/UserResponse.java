package com.sogeco.fleet.modules.user.dto;

import com.sogeco.fleet.common.enums.UserStatus;
import com.sogeco.fleet.modules.user.User;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        Long cityId,
        String cityName,
        UserStatus status,
        Set<String> roles,
        boolean totpEnabled,
        boolean mustChangePassword,
        boolean locked,
        Instant lastLoginAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getFullName(),
                user.getPhone(),
                user.getCity() == null ? null : user.getCity().getId(),
                user.getCity() == null ? null : user.getCity().getName(),
                user.getStatus(),
                user.getRoleCodes(),
                Boolean.TRUE.equals(user.getTotpEnabled()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.isLocked(),
                user.getLastLoginAt());
    }
}
