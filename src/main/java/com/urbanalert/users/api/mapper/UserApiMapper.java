package com.urbanalert.users.api.mapper;

import com.urbanalert.users.api.dto.UserResponse;
import com.urbanalert.users.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserApiMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
