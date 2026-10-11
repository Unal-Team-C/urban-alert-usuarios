package com.urbanalert.users.infrastructure.config;

import com.urbanalert.users.domain.model.Role;
import com.urbanalert.users.infrastructure.persistence.entity.UserJpaEntity;
import com.urbanalert.users.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("local")
public class DataInitializer implements ApplicationRunner {

    private record SeedUser(String id, String name, String email, Role role) {}

    private static final List<SeedUser> SEEDS = List.of(
        new SeedUser("018f4c2a-0000-7000-8000-000000000001", "Administrator", "admin@urbanalert.com",     Role.ADMIN),
        new SeedUser("018f4c2a-0000-7000-8000-000000000002", "Usuario Dos",   "usuario02@example.com",    Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000003", "Usuario Tres",  "usuario03@example.com",    Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000004", "Usuario Cuatro","usuario04@example.com",    Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000005", "Usuario Cinco", "usuario05@example.com",    Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000006", "Usuario Seis",  "usuario06@example.com",    Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000007", "Usuario Siete", "usuario07@example.com",    Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000008", "test-user",     "urbanalerttest@proton.me", Role.USER)
    );

    private final UserJpaRepository userJpaRepository;

    public DataInitializer(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (SeedUser seed : SEEDS) {
            UUID id = UUID.fromString(seed.id());
            if (userJpaRepository.existsById(id)) continue;

            UserJpaEntity entity = new UserJpaEntity();
            entity.setId(id);
            entity.setName(seed.name());
            entity.setEmail(seed.email());
            entity.setRole(seed.role());
            userJpaRepository.save(entity);
        }
    }
}
