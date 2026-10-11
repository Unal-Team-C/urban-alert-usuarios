package com.urbanalert.users.application.port.in;

import com.urbanalert.users.domain.model.User;

import java.util.List;

public interface GetUsersUseCase {
    List<User> getUsers();
}
