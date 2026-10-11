package com.urbanalert.users.application.port.in;

import com.urbanalert.users.domain.model.User;

import java.util.UUID;

public interface GetUserByIdUseCase {
    User getUserById(UUID id);
}
