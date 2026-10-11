package com.urbanalert.users;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the cloud configuration (spec 001-migracion-aws RF-001..RF-005, RF-008): the "aws" profile used by the
 * ECS service and the "migrate" profile layered on top of it by the one-off migration task.
 */
class AwsProfileConfigTest {

    @Test
    void awsProfileReadsTheDatabaseFromTheEnvironmentAndNeverMigrates() throws IOException {
        PropertySource<?> aws = load("application-aws.yml");

        assertThat(aws.getProperty("spring.datasource.url")).hasToString("${DB_URL}");
        assertThat(aws.getProperty("spring.datasource.username")).hasToString("${DB_USERNAME}");
        assertThat(aws.getProperty("spring.datasource.password")).hasToString("${DB_PASSWORD}");
        assertThat(aws.getProperty("spring.datasource.hikari.maximum-pool-size")).hasToString("${DB_POOL_MAX}");
        assertThat(aws.getProperty("spring.datasource.hikari.minimum-idle")).hasToString("${DB_POOL_MIN}");
        assertThat(aws.getProperty("spring.flyway.enabled")).isEqualTo(false);
        assertThat(aws.getProperty("spring.jpa.hibernate.ddl-auto")).hasToString("validate");
    }

    @Test
    void awsProfileLogsJsonAndShutsDownGracefully() throws IOException {
        PropertySource<?> aws = load("application-aws.yml");

        assertThat(aws.getProperty("logging.structured.format.console")).hasToString("ecs");
        assertThat(aws.getProperty("server.shutdown")).hasToString("graceful");
        // Below the 30 s ECS stop timeout.
        assertThat(aws.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).hasToString("20s");
    }

    @Test
    void awsProfileDoesNotServeApiDocumentation() throws IOException {
        PropertySource<?> aws = load("application-aws.yml");

        assertThat(aws.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(aws.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
    }

    @Test
    void migrateProfileRunsFlywayWithTheCloudCallbackAndNoWebServer() throws IOException {
        PropertySource<?> migrate = load("application-migrate.yml");

        assertThat(migrate.getProperty("spring.main.web-application-type")).hasToString("none");
        assertThat(migrate.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(migrate.getProperty("spring.flyway.locations").toString())
                .contains("classpath:db/migration")
                .contains("classpath:db/cloud");
        assertThat(migrate.getProperty("spring.flyway.placeholders.app_user")).hasToString("${DB_APP_USER}");
        assertThat(migrate.getProperty("spring.flyway.placeholders.app_password")).hasToString("${DB_APP_PASSWORD}");
    }

    @Test
    void cloudCallbackIsNotLoadedByTheDefaultProfile() throws IOException {
        PropertySource<?> defaults = load("application.yml");

        assertThat(defaults.getProperty("spring.flyway.locations").toString()).doesNotContain("db/cloud");
    }

    private static PropertySource<?> load(String name) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
        assertThat(sources).as(name + " must exist and contain one document").hasSize(1);
        return sources.get(0);
    }
}
