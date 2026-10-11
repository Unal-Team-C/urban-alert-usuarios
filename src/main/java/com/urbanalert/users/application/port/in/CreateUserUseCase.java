package com.urbanalert.users.application.port.in;

import com.urbanalert.users.domain.model.Role;

import java.util.UUID;

public interface CreateUserUseCase {
    UUID createUser(CreateUserCommand command);

    record CreateUserCommand(String name, String email, Role role) {}
}
