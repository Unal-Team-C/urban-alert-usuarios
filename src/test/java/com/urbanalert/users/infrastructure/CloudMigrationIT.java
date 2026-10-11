package com.urbanalert.users.infrastructure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the one-off migration task does in the cloud (spec 001-migracion-aws RF-008..RF-010, plan D-002/D-003):
 * Flyway with db/migration + db/cloud as the administrator, which also creates the application role.
 * Methods run in order against one database, like consecutive deployments.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CloudMigrationIT {

    private static final String APP_USER = "users_app";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    @Order(1)
    void firstDeploymentCreatesTheSchemaTheAnonymizedSeedAndTheApplicationRole() throws SQLException {
        MigrateResult result = migrate("first-password");

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(2);
        try (Connection admin = adminConnection()) {
            assertThat(count(admin, "SELECT count(*) FROM users")).isEqualTo(8);
            assertThat(count(admin, "SELECT count(*) FROM users WHERE name = 'test-user' AND email = 'urbanalerttest@proton.me'"))
                    .isEqualTo(1);
            assertThat(count(admin, "SELECT count(*) FROM users WHERE role = 'ADMIN'")).isEqualTo(1);
            assertThat(count(admin, "SELECT count(*) FROM users WHERE email LIKE '%gmail.com'")).isZero();
        }
    }

    @Test
    @Order(2)
    void repeatingTheDeploymentChangesNothing() throws SQLException {
        MigrateResult result = migrate("first-password");

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isZero();
        try (Connection admin = adminConnection()) {
            assertThat(count(admin, "SELECT count(*) FROM users")).isEqualTo(8);
        }
    }

    @Test
    @Order(3)
    void applicationRoleCanReadAndWriteUsersButNotChangeTheSchema() throws SQLException {
        try (Connection app = appConnection("first-password"); Statement st = app.createStatement()) {
            assertThat(count(app, "SELECT count(*) FROM users")).isEqualTo(8);
            st.executeUpdate("INSERT INTO users (id, name, email, role) VALUES "
                    + "('018f4c2a-0000-7000-8000-0000000000aa', 'Temporal', 'temporal@example.com', 'USER')");
            st.executeUpdate("UPDATE users SET name = 'Temporal 2' WHERE email = 'temporal@example.com'");
            assertThat(st.executeUpdate("DELETE FROM users WHERE email = 'temporal@example.com'")).isEqualTo(1);

            assertThatThrownBy(() -> st.execute("CREATE TABLE intruder (id int)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.execute("DROP TABLE users"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("must be owner");
        }
    }

    @Test
    @Order(4)
    void aNewPasswordIsSynchronizedOnTheNextDeployment() throws SQLException {
        migrate("second-password");

        try (Connection app = appConnection("second-password")) {
            assertThat(count(app, "SELECT count(*) FROM users")).isEqualTo(8);
        }
        assertThatThrownBy(() -> appConnection("first-password").close())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("password authentication failed");
    }

    private static MigrateResult migrate(String appPassword) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/cloud")
                .placeholders(Map.of("app_user", APP_USER, "app_password", appPassword))
                .load()
                .migrate();
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Connection appConnection(String password) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, password);
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
