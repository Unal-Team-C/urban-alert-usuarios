package com.urbanalert.users.domain.port.out;

import com.urbanalert.users.domain.model.Role;
import com.urbanalert.users.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    List<User> findAll();
    void delete(User user);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, UUID excludedId);
    long countByRole(Role role);
}
