package com.urbanalert.users.api.controller;

import com.urbanalert.users.api.dto.*;
import com.urbanalert.users.api.mapper.UserApiMapper;
import com.urbanalert.users.application.port.in.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final GetUsersUseCase getUsersUseCase;
    private final GetUserByIdUseCase getUserByIdUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final UpdateUserRoleUseCase updateUserRoleUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final UserApiMapper mapper;

    public UserController(
            CreateUserUseCase createUserUseCase,
            GetUsersUseCase getUsersUseCase,
            GetUserByIdUseCase getUserByIdUseCase,
            UpdateUserUseCase updateUserUseCase,
            UpdateUserRoleUseCase updateUserRoleUseCase,
            DeleteUserUseCase deleteUserUseCase,
            UserApiMapper mapper) {
        this.createUserUseCase = createUserUseCase;
        this.getUsersUseCase = getUsersUseCase;
        this.getUserByIdUseCase = getUserByIdUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.updateUserRoleUseCase = updateUserRoleUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UUID id = createUserUseCase.createUser(new CreateUserUseCase.CreateUserCommand(
                request.name(), request.email(), request.role()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", id,
                "message", "User created"
        ));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        List<UserResponse> users = getUsersUseCase.getUsers().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(getUserByIdUseCase.getUserById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        updateUserUseCase.updateUser(id, new UpdateUserUseCase.UpdateUserCommand(
                request.name(), request.email(), request.role()
        ));
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        updateUserRoleUseCase.updateUserRole(id, request.role());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        deleteUserUseCase.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
