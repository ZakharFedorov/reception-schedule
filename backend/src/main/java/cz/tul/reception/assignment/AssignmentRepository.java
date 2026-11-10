package cz.tul.reception.assignment;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AssignmentRepository {

    record Shift(long blockId, OffsetDateTime start, OffsetDateTime end) {}

    private final JdbcClient jdbc;

    AssignmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Long> adminBlock(String login) {
        return jdbc.sql("""
                SELECT e.home_block_id FROM local_account a
                JOIN employee e ON e.id = a.employee_id
                WHERE a.login = ? AND e.block_admin
                FOR SHARE OF e, a
                """)
                .param(login).query(Long.class).optional();
    }

    Optional<Shift> shift(long shiftId) {
        return jdbc.sql("SELECT block_id, starts_at, ends_at FROM shift WHERE id = ? FOR UPDATE")
                .param(shiftId)
                .query((rs, row) -> new Shift(rs.getLong("block_id"),
                        rs.getObject("starts_at", OffsetDateTime.class),
                        rs.getObject("ends_at", OffsetDateTime.class)))
                .optional();
    }

    boolean employeeExists(long employeeId) {
        return jdbc.sql("SELECT id FROM employee WHERE id = ? FOR KEY SHARE")
                .param(employeeId).query(Long.class).optional().isPresent();
    }

    long insert(long shiftId, CreateAssignment request) {
        return jdbc.sql("""
                INSERT INTO shift_assignment (shift_id, employee_id, starts_at, ends_at)
                VALUES (?, ?, ?, ?) RETURNING id
                """)
                .params(shiftId, request.employeeId(), request.startsAt(), request.endsAt()).query(Long.class).single();
    }
}
