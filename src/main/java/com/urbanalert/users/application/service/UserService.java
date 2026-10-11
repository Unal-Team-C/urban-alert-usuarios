package com.urbanalert.users.application.service;

import com.urbanalert.users.application.port.in.*;
import com.urbanalert.users.domain.exception.DuplicateEmailException;
import com.urbanalert.users.domain.exception.LastAdminException;
import com.urbanalert.users.domain.exception.UserNotFoundException;
import com.urbanalert.users.domain.model.Role;
import com.urbanalert.users.domain.model.User;
import com.urbanalert.users.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UserService implements
        CreateUserUseCase,
        GetUsersUseCase,
        GetUserByIdUseCase,
        UpdateUserUseCase,
        UpdateUserRoleUseCase,
        DeleteUserUseCase {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UUID createUser(CreateUserCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new DuplicateEmailException(command.email());
        }
        User user = new User(command.name(), command.email(), command.role());
        userRepository.save(user);
        return user.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public void updateUser(UUID id, UpdateUserCommand command) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (userRepository.existsByEmailAndIdNot(command.email(), id)) {
            throw new DuplicateEmailException(command.email());
        }

        user.updateProfile(command.name(), command.email(), command.role());
        userRepository.save(user);
    }

    @Override
    public void updateUserRole(UUID id, Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (user.getRole() == Role.ADMIN && role == Role.USER
                && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        user.updateRole(role);
        userRepository.save(user);
    }

    @Override
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (user.getRole() == Role.ADMIN
                && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        userRepository.delete(user);
    }
}
