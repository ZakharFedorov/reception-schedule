package cz.tul.reception.assignment;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RECEPTION_TEST_DB_URL", matches = "jdbc:postgresql:.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrentAssignmentTest {

    private static final String SCHEMA = "assignment_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String LOGIN = "admin-b@example.test";
    private static final String PASSWORD = "test-only";

    @DynamicPropertySource
    static void testDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("RECEPTION_TEST_DB_URL"));
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA + ", public");
    }

    @Autowired JdbcClient jdbc;
    @Autowired DataSource dataSource;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;

    @AfterAll
    void dropTestSchema() {
        jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
    }

    @Test
    void assignmentsStayWithinTheirShiftAcrossMidnightAndMonthEnd() throws Exception {
        var script = new ClassPathResource("sql/assignment-bounds.sql");
        jdbc.sql(script.getContentAsString(StandardCharsets.UTF_8)).update();
    }

    @ParameterizedTest(name = "Concurrent shift shortening and assignment (insert first = {0})")
    @ValueSource(booleans = {true, false})
    void shorteningAndAssigningCannotCommitIncompatibleBounds(boolean insertFirst) throws Exception {
        jdbc.sql("INSERT INTO block (code) VALUES ('D') ON CONFLICT (code) DO NOTHING").update();
        long block = jdbc.sql("SELECT id FROM block WHERE code = 'D'").query(Long.class).single();
        long worker = employee(block, false);
        long shift = jdbc.sql("""
                INSERT INTO shift (block_id, starts_at, ends_at)
                VALUES (?, '2026-11-11 14:00+01', '2026-11-11 22:00+01') RETURNING id
                """).param(block).query(Long.class).single();

        try (var pool = Executors.newSingleThreadExecutor();
             var first = dataSource.getConnection();
             var second = dataSource.getConnection()) {
            first.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            second.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            first.setAutoCommit(false);
            int secondPid;
            try (var stmt = second.createStatement(); var rows = stmt.executeQuery("SELECT pg_backend_pid()")) {
                rows.next();
                secondPid = rows.getInt(1);
            }
            try {
                changeBounds(first, insertFirst, shift, worker);
                var result = pool.submit(() -> {
                    try {
                        changeBounds(second, !insertFirst, shift, worker);
                        return "committed";
                    } catch (SQLException ex) {
                        return ex.getSQLState();
                    }
                });
                awaitBlockedByFirst(first, secondPid);
                first.commit();
                assertThat(result.get(15, TimeUnit.SECONDS)).isEqualTo("23514");
            } finally {
                first.rollback();
            }
        }

        assertThat(jdbc.sql("SELECT count(*) FROM shift_assignment WHERE shift_id = ?")
                .param(shift).query(Integer.class).single()).isEqualTo(insertFirst ? 1 : 0);
        String expectedEnd = insertFirst ? "2026-11-11 22:00+01" : "2026-11-11 21:00+01";
        assertThat(jdbc.sql("SELECT ends_at = CAST(? AS timestamptz) FROM shift WHERE id = ?")
                .params(expectedEnd, shift).query(Boolean.class).single()).isTrue();
    }

    private static void changeBounds(Connection connection, boolean insert, long shift, long worker)
            throws SQLException {
        String sql = insert ? """
                INSERT INTO shift_assignment (shift_id, employee_id, starts_at, ends_at)
                VALUES (?, ?, '2026-11-11 19:00+01', '2026-11-11 22:00+01')
                """ : "UPDATE shift SET ends_at = '2026-11-11 21:00+01' WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, shift);
            if (insert) {
                stmt.setLong(2, worker);
            }
            stmt.setQueryTimeout(20);
            assertThat(stmt.executeUpdate()).isEqualTo(1);
        }
    }

    private static void awaitBlockedByFirst(Connection first, int secondPid) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        try (var check = first.prepareStatement("SELECT pg_backend_pid() = ANY(pg_blocking_pids(?))")) {
            check.setInt(1, secondPid);
            while (System.nanoTime() < deadline) {
                try (var rows = check.executeQuery()) {
                    rows.next();
                    if (rows.getBoolean(1)) {
                        return;
                    }
                }
                Thread.sleep(20);
            }
        }
        throw new AssertionError("PostgreSQL did not report a lock conflict between the two transactions");
    }

    @Test
    void concurrentRequestsForOneShiftPersistOnlyOneAssignment() throws Exception {
        long blockA = jdbc.sql("INSERT INTO block (code) VALUES ('A') RETURNING id").query(Long.class).single();
        long blockB = jdbc.sql("INSERT INTO block (code) VALUES ('B') RETURNING id").query(Long.class).single();
        long admin = employee(blockB, true);
        jdbc.sql("INSERT INTO local_account (employee_id, login, password_hash) VALUES (?, ?, ?)")
                .params(admin, LOGIN, encoder.encode(PASSWORD)).update();
        long first = employee(blockA, false);
        long second = employee(blockA, false);
        long shift = jdbc.sql("""
                INSERT INTO shift (block_id, starts_at, ends_at)
                VALUES (?, '2026-11-11 14:00+01', '2026-11-11 22:00+01') RETURNING id
                """).param(blockB).query(Long.class).single();

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> assign(shift, first, ready, start));
            var b = pool.submit(() -> assign(shift, second, ready, start));
            try {
                assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.sql("SELECT employee_id FROM shift_assignment WHERE shift_id = ?")
                .param(shift).query(Long.class).single()).isIn(first, second);
        assertThat(jdbc.sql("SELECT home_block_id FROM employee WHERE id IN (?, ?)")
                .params(first, second).query(Long.class).list()).containsExactly(blockA, blockA);
    }

    private long employee(long home, boolean admin) {
        return jdbc.sql("""
                INSERT INTO employee (home_block_id, first_name, last_name, block_admin)
                VALUES (?, 'Test', 'Employee', ?) RETURNING id
                """).params(home, admin).query(Long.class).single();
    }

    private int assign(long shift, long employee, CountDownLatch ready, CountDownLatch start) throws Exception {
        String auth = "Basic " + HttpHeaders.encodeBasicAuth(LOGIN, PASSWORD, null);
        var csrf = mvc.perform(get("/api/csrf").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andReturn();
        var token = json.readTree(csrf.getResponse().getContentAsByteArray());
        ready.countDown();
        if (!start.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent request did not start");
        }
        return mvc.perform(post("/api/shifts/{shift}/assignments", shift)
                .session((MockHttpSession) csrf.getRequest().getSession(false))
                .header(HttpHeaders.AUTHORIZATION, auth)
                .header(token.get("headerName").asString(), token.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"employeeId": %d, "startsAt": "2026-11-11T14:00:00+01:00", "endsAt": "2026-11-11T22:00:00+01:00"}
                        """.formatted(employee))).andReturn().getResponse().getStatus();
    }
}
