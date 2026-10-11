package com.urbanalert.users.infrastructure.persistence.repository;

import com.urbanalert.users.domain.model.Role;
import com.urbanalert.users.domain.model.User;
import com.urbanalert.users.domain.port.out.UserRepository;
import com.urbanalert.users.infrastructure.persistence.entity.UserJpaEntity;
import com.urbanalert.users.infrastructure.persistence.mapper.UserPersistenceMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository, UserPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        UserJpaEntity saved = jpaRepository.save(mapper.toEntity(user));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<User> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void delete(User user) {
        jpaRepository.deleteById(user.getId());
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, UUID excludedId) {
        return jpaRepository.existsByEmailAndIdNot(email, excludedId);
    }

    @Override
    public long countByRole(Role role) {
        return jpaRepository.countByRole(role);
    }
}
