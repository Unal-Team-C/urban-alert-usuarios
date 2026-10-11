package com.urbanalert.users.api.dto;

import com.urbanalert.users.domain.model.Role;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Role role) {}
