package com.urbanalert.users.domain;

import com.urbanalert.users.domain.model.Role;
import com.urbanalert.users.domain.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    void createUser_withValidData_assignsUuidAndDefaultsApplied() {
        User user = new User("Ana Torres", "ana@example.com", Role.USER);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getName()).isEqualTo("Ana Torres");
        assertThat(user.getEmail()).isEqualTo("ana@example.com");
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void createUser_withNullRole_defaultsToUSER() {
        User user = new User("Luis", "luis@example.com", null);

        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void createUser_withAdminRole_setsAdmin() {
        User user = new User("Admin", "admin@example.com", Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void createUser_withNullName_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new User(null, "a@example.com", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name is required");
    }

    @Test
    void createUser_withBlankName_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new User("   ", "a@example.com", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name is required");
    }

    @Test
    void createUser_withNameExceedingMaxLength_throwsIllegalArgumentException() {
        String longName = "A".repeat(User.NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> new User(longName, "a@example.com", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name cannot exceed");
    }

    @Test
    void createUser_withNullEmail_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new User("Ana", null, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is required");
    }

    @Test
    void createUser_withBlankEmail_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new User("Ana", "  ", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is required");
    }

    @Test
    void createUser_withInvalidEmailFormat_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new User("Ana", "not-an-email", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");
    }

    @Test
    void createUser_withEmailExceedingMaxLength_throwsIllegalArgumentException() {
        String longEmail = "a".repeat(User.EMAIL_MAX_LENGTH) + "@x.com";

        assertThatThrownBy(() -> new User("Ana", longEmail, Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email cannot exceed");
    }

    @Test
    void updateProfile_withValidData_replacesAllFields() {
        User user = new User("Old Name", "old@example.com", Role.USER);

        user.updateProfile("New Name", "new@example.com", Role.ADMIN);

        assertThat(user.getName()).isEqualTo("New Name");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void updateProfile_withInvalidEmail_throwsIllegalArgumentException() {
        User user = new User("Ana", "ana@example.com", Role.USER);

        assertThatThrownBy(() -> user.updateProfile("Ana", "bad-email", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");
    }

    @Test
    void updateRole_withValidRole_changesRoleOnly() {
        User user = new User("Ana", "ana@example.com", Role.USER);

        user.updateRole(Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void updateRole_withNull_throwsIllegalArgumentException() {
        User user = new User("Ana", "ana@example.com", Role.USER);

        assertThatThrownBy(() -> user.updateRole(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role is required");
    }

    @Test
    void idIsAssignedByConstructor_andIsNotNull() {
        User first  = new User("A", "a@x.com", Role.USER);
        User second = new User("B", "b@x.com", Role.USER);

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isNotNull();
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void reconstitute_buildsUserWithProvidedId() {
        java.util.UUID fixedId = java.util.UUID.randomUUID();

        User user = User.reconstitute(fixedId, "Ana", "ana@example.com", Role.USER);

        assertThat(user.getId()).isEqualTo(fixedId);
        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getEmail()).isEqualTo("ana@example.com");
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }
}
