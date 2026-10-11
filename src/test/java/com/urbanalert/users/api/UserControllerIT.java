package com.urbanalert.users.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanalert.users.api.dto.CreateUserRequest;
import com.urbanalert.users.api.dto.UpdateUserRequest;
import com.urbanalert.users.api.dto.UpdateUserRoleRequest;
import com.urbanalert.users.domain.model.Role;
import com.urbanalert.users.infrastructure.persistence.entity.UserJpaEntity;
import com.urbanalert.users.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserJpaRepository userJpaRepository;

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
    }

    // ─── POST /api/v1/users ───────────────────────────────────────────────────

    @Test
    void POST_createUser_validPayload_returns201WithId() throws Exception {
        CreateUserRequest request = new CreateUserRequest("Ana Torres", "ana@example.com", Role.USER);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.message").value("User created"));
    }

    @Test
    void POST_createUser_noRoleProvided_defaultsToUSER() throws Exception {
        String body = """
                { "name": "Luis", "email": "luis@example.com" }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        UserJpaEntity saved = userJpaRepository.findAll().get(0);
        assert saved.getRole() == Role.USER;
    }

    @Test
    void POST_createUser_duplicateEmail_returns409() throws Exception {
        insertUser("Ana", "ana@example.com", Role.USER);

        CreateUserRequest request = new CreateUserRequest("Other", "ana@example.com", Role.USER);
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void POST_createUser_missingName_returns400() throws Exception {
        String body = """
                { "email": "ana@example.com", "role": "USER" }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void POST_createUser_invalidEmailFormat_returns400() throws Exception {
        CreateUserRequest request = new CreateUserRequest("Ana", "not-an-email", Role.USER);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void POST_createUser_invalidRoleValue_returns400() throws Exception {
        String body = """
                { "name": "Ana", "email": "ana@example.com", "role": "SUPERUSER" }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ─── GET /api/v1/users ────────────────────────────────────────────────────

    @Test
    void GET_listUsers_withExistingUsers_returns200WithArray() throws Exception {
        insertUser("Ana", "ana@example.com", Role.USER);
        insertUser("Luis", "luis@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].email", containsInAnyOrder("ana@example.com", "luis@example.com")));
    }

    @Test
    void GET_listUsers_emptyDatabase_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ─── GET /api/v1/users/{id} ───────────────────────────────────────────────

    @Test
    void GET_getUserById_existingUser_returns200WithDetails() throws Exception {
        UUID id = insertUser("Ana", "ana@example.com", Role.USER);

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void GET_getUserById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void GET_getUserById_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ─── PUT /api/v1/users/{id} ───────────────────────────────────────────────

    @Test
    void PUT_updateUser_validPayload_returns200() throws Exception {
        UUID id = insertUser("Old Name", "old@example.com", Role.USER);
        UpdateUserRequest request = new UpdateUserRequest("New Name", "new@example.com", Role.ADMIN);

        mockMvc.perform(put("/api/v1/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        UserJpaEntity updated = userJpaRepository.findById(id).orElseThrow();
        assert updated.getName().equals("New Name");
        assert updated.getRole() == Role.ADMIN;
    }

    @Test
    void PUT_updateUser_sameEmail_doesNotConflict() throws Exception {
        UUID id = insertUser("Ana", "ana@example.com", Role.USER);
        UpdateUserRequest request = new UpdateUserRequest("Ana Updated", "ana@example.com", Role.USER);

        mockMvc.perform(put("/api/v1/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void PUT_updateUser_emailCollisionWithAnotherUser_returns409() throws Exception {
        insertUser("User A", "a@example.com", Role.USER);
        UUID idB = insertUser("User B", "b@example.com", Role.USER);

        UpdateUserRequest request = new UpdateUserRequest("User B", "a@example.com", Role.USER);
        mockMvc.perform(put("/api/v1/users/{id}", idB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void PUT_updateUser_unknownId_returns404() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("Name", "mail@x.com", Role.USER);

        mockMvc.perform(put("/api/v1/users/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void PUT_updateUser_invalidEmail_returns400() throws Exception {
        UUID id = insertUser("Ana", "ana@example.com", Role.USER);
        UpdateUserRequest request = new UpdateUserRequest("Ana", "bad-email", Role.USER);

        mockMvc.perform(put("/api/v1/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /api/v1/users/{id}/role ───────────────────────────────────────

    @Test
    void PATCH_updateRole_userToAdmin_returns200() throws Exception {
        UUID id = insertUser("Ana", "ana@example.com", Role.USER);
        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.ADMIN);

        mockMvc.perform(patch("/api/v1/users/{id}/role", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assert userJpaRepository.findById(id).orElseThrow().getRole() == Role.ADMIN;
    }

    @Test
    void PATCH_updateRole_invalidRoleValue_returns400() throws Exception {
        UUID id = insertUser("Ana", "ana@example.com", Role.USER);
        String body = """
                { "role": "MODERATOR" }
                """;

        mockMvc.perform(patch("/api/v1/users/{id}/role", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PATCH_updateRole_unknownId_returns404() throws Exception {
        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.ADMIN);

        mockMvc.perform(patch("/api/v1/users/{id}/role", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/v1/users/{id} ────────────────────────────────────────────

    @Test
    void DELETE_deleteUser_regularUser_returns204() throws Exception {
        UUID id = insertUser("Ana", "ana@example.com", Role.USER);

        mockMvc.perform(delete("/api/v1/users/{id}", id))
                .andExpect(status().isNoContent());

        assert userJpaRepository.findById(id).isEmpty();
    }

    @Test
    void DELETE_deleteUser_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void DELETE_deleteUser_lastAdmin_returns409() throws Exception {
        UUID adminId = insertUser("Admin", "admin@example.com", Role.ADMIN);

        mockMvc.perform(delete("/api/v1/users/{id}", adminId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        assert userJpaRepository.findById(adminId).isPresent();
    }

    @Test
    void DELETE_deleteUser_oneOfMultipleAdmins_returns204() throws Exception {
        UUID admin1 = insertUser("Admin 1", "admin1@example.com", Role.ADMIN);
        insertUser("Admin 2", "admin2@example.com", Role.ADMIN);
        insertUser("Admin 3", "admin3@example.com", Role.ADMIN);

        mockMvc.perform(delete("/api/v1/users/{id}", admin1))
                .andExpect(status().isNoContent());
    }

    // ─── Health ───────────────────────────────────────────────────────────────

    @Test
    void GET_health_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UUID insertUser(String name, String email, Role role) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate());
        entity.setName(name);
        entity.setEmail(email);
        entity.setRole(role);
        return userJpaRepository.save(entity).getId();
    }
}
