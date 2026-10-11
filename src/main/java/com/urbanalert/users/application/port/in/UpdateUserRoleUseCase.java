package com.urbanalert.users.application.port.in;

import com.urbanalert.users.domain.model.Role;

import java.util.UUID;

public interface UpdateUserRoleUseCase {
    void updateUserRole(UUID id, Role role);
}
