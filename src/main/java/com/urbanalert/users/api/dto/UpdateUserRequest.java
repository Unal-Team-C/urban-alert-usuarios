package com.urbanalert.users.api.dto;

import com.urbanalert.users.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name cannot exceed 200 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 300, message = "Email cannot exceed 300 characters")
        String email,

        @NotNull(message = "Role is required")
        Role role
) {}
