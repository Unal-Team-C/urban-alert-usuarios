package com.urbanalert.users.domain.model;

import com.fasterxml.uuid.Generators;

import java.util.UUID;
import java.util.regex.Pattern;

public class User {

    public static final int NAME_MAX_LENGTH = 200;
    public static final int EMAIL_MAX_LENGTH = 300;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private UUID id;
    private String name;
    private String email;
    private Role role;

    public User(String name, String email, Role role) {
        this.id = Generators.timeBasedEpochGenerator().generate();
        applyName(name);
        applyEmail(email);
        this.role = role != null ? role : Role.USER;
    }

    private User() {}

    public static User reconstitute(UUID id, String name, String email, Role role) {
        User user = new User();
        user.id = id;
        user.name = name;
        user.email = email;
        user.role = role;
        return user;
    }

    public void updateProfile(String name, String email, Role role) {
        applyName(name);
        applyEmail(email);
        this.role = role != null ? role : Role.USER;
    }

    public void updateRole(Role role) {
        if (role == null) throw new IllegalArgumentException("Role is required");
        this.role = role;
    }

    private void applyName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name is required");
        if (name.length() > NAME_MAX_LENGTH)
            throw new IllegalArgumentException("Name cannot exceed " + NAME_MAX_LENGTH + " characters");
        this.name = name;
    }

    private void applyEmail(String email) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email is required");
        if (email.length() > EMAIL_MAX_LENGTH)
            throw new IllegalArgumentException("Email cannot exceed " + EMAIL_MAX_LENGTH + " characters");
        if (!EMAIL_PATTERN.matcher(email).matches())
            throw new IllegalArgumentException("Invalid email format");
        this.email = email;
    }

    public UUID getId()    { return id; }
    public String getName()  { return name; }
    public String getEmail() { return email; }
    public Role getRole()    { return role; }
}
