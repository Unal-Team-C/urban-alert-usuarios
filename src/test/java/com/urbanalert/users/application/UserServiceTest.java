package com.urbanalert.users.application;

import com.urbanalert.users.application.port.in.*;
import com.urbanalert.users.application.service.UserService;
import com.urbanalert.users.domain.exception.DuplicateEmailException;
import com.urbanalert.users.domain.exception.LastAdminException;
import com.urbanalert.users.domain.exception.UserNotFoundException;
import com.urbanalert.users.domain.model.Role;
import com.urbanalert.users.domain.model.User;
import com.urbanalert.users.domain.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    // ─── createUser ───────────────────────────────────────────────────────────

    @Test
    void createUser_withUniqueEmail_savesAndReturnsId() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id = userService.createUser(
                new CreateUserUseCase.CreateUserCommand("Ana", "ana@example.com", Role.USER));

        assertThat(id).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_withDuplicateEmail_throwsDuplicateEmailException() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                new CreateUserUseCase.CreateUserCommand("Ana", "ana@example.com", Role.USER)))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_withNullRole_persistsUserWithDefaultRoleUSER() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(
                new CreateUserUseCase.CreateUserCommand("Ana", "ana@example.com", null));

        verify(userRepository).save(argThat(u -> u.getRole() == Role.USER));
    }

    // ─── getUsers ─────────────────────────────────────────────────────────────

    @Test
    void getUsers_returnsAllUsersFromRepository() {
        User u1 = new User("Ana", "ana@example.com", Role.USER);
        User u2 = new User("Luis", "luis@example.com", Role.ADMIN);
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        List<User> result = userService.getUsers();

        assertThat(result).hasSize(2);
    }

    @Test
    void getUsers_whenEmpty_returnsEmptyList() {
        when(userRepository.findAll()).thenReturn(List.of());

        assertThat(userService.getUsers()).isEmpty();
    }

    // ─── getUserById ──────────────────────────────────────────────────────────

    @Test
    void getUserById_withExistingId_returnsUser() {
        User user = new User("Ana", "ana@example.com", Role.USER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        User result = userService.getUserById(user.getId());

        assertThat(result.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void getUserById_withUnknownId_throwsUserNotFoundException() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(unknown))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─── updateUser ───────────────────────────────────────────────────────────

    @Test
    void updateUser_withValidData_updatesAndSaves() {
        User user = new User("Old", "old@example.com", Role.USER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("new@example.com", user.getId())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(user.getId(),
                new UpdateUserUseCase.UpdateUserCommand("New", "new@example.com", Role.ADMIN));

        assertThat(user.getName()).isEqualTo("New");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void updateUser_withSameEmail_doesNotThrowConflict() {
        User user = new User("Ana", "ana@example.com", Role.USER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("ana@example.com", user.getId())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(() ->
                userService.updateUser(user.getId(),
                        new UpdateUserUseCase.UpdateUserCommand("Ana Updated", "ana@example.com", Role.USER)));
    }

    @Test
    void updateUser_withEmailOfAnotherUser_throwsDuplicateEmailException() {
        User user = new User("Ana", "ana@example.com", Role.USER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("other@example.com", user.getId())).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(user.getId(),
                new UpdateUserUseCase.UpdateUserCommand("Ana", "other@example.com", Role.USER)))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void updateUser_withUnknownId_throwsUserNotFoundException() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(unknown,
                new UpdateUserUseCase.UpdateUserCommand("X", "x@x.com", Role.USER)))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─── updateUserRole ───────────────────────────────────────────────────────

    @Test
    void updateUserRole_promoteUserToAdmin_succeeds() {
        User user = new User("Ana", "ana@example.com", Role.USER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUserRole(user.getId(), Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void updateUserRole_demoteOneOfMultipleAdmins_succeeds() {
        User user = new User("Ana", "ana@example.com", Role.ADMIN);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUserRole(user.getId(), Role.USER);

        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void updateUserRole_demoteLastAdmin_throwsLastAdminException() {
        User user = new User("Admin", "admin@example.com", Role.ADMIN);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateUserRole(user.getId(), Role.USER))
                .isInstanceOf(LastAdminException.class);
    }

    @Test
    void updateUserRole_withUnknownId_throwsUserNotFoundException() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserRole(unknown, Role.USER))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─── deleteUser ───────────────────────────────────────────────────────────

    @Test
    void deleteUser_regularUser_deletesSuccessfully() {
        User user = new User("Ana", "ana@example.com", Role.USER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.deleteUser(user.getId());

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_lastAdmin_throwsLastAdminException() {
        User admin = new User("Admin", "admin@example.com", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteUser(admin.getId()))
                .isInstanceOf(LastAdminException.class);

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUser_oneOfMultipleAdmins_deletesSuccessfully() {
        User admin = new User("Admin", "admin@example.com", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(3L);

        userService.deleteUser(admin.getId());

        verify(userRepository).delete(admin);
    }

    @Test
    void deleteUser_withUnknownId_throwsUserNotFoundException() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(unknown))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).delete(any());
    }
}
