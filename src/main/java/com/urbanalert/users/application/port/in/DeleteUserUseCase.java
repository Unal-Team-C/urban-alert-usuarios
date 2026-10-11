package com.urbanalert.users.application.port.in;

import java.util.UUID;

public interface DeleteUserUseCase {
    void deleteUser(UUID id);
}
