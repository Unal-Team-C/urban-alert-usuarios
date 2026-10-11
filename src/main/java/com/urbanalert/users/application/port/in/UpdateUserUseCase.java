package com.urbanalert.users.application.port.in;

import com.urbanalert.users.domain.model.Role;

import java.util.UUID;

public interface UpdateUserUseCase {
    void updateUser(UUID id, UpdateUserCommand command);

    record UpdateUserCommand(String name, String email, Role role) {}
}
