package com.urbanalert.users.infrastructure.persistence.repository;

import com.urbanalert.users.domain.model.Role;
import com.urbanalert.users.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, UUID id);
    long countByRole(Role role);
}
