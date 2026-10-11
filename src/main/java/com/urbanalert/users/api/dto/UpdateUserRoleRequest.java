package com.urbanalert.users.api.dto;

import com.urbanalert.users.domain.model.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(

        @NotNull(message = "Role is required")
        Role role
) {}
