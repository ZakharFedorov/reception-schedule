package cz.tul.reception.assignment;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;

    @AfterAll
    void dropTestSchema() {
        jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
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
