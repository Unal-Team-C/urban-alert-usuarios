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
        new SeedUser("018f4c2a-0000-7000-8000-000000000001", "Administrator",   "admin@urbanalert.com",           Role.ADMIN),
        new SeedUser("018f4c2a-0000-7000-8000-000000000002", "Felipe Caslo",    "facaslo.99@gmail.com",           Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000003", "Julian Da Rivas", "juliandarivas@gmail.com",        Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000004", "Luis Gomez Banoy","luisgomezbanoy@gmail.com",       Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000005", "Diana Fer",       "dianafer0814@gmail.com",         Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000006", "Nelson Ferrucho", "nelson.ferrucho.unal@gmail.com", Role.USER),
        new SeedUser("018f4c2a-0000-7000-8000-000000000007", "Andres Lugo",     "anfellr11@gmail.com",            Role.USER)
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
