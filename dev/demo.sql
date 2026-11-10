-- Load once into a freshly migrated local development database.
DO $$
DECLARE
    block_a bigint;
    block_b bigint;
    demo_employee bigint;
    shift_a bigint;
    shift_b bigint;
    shift_start timestamptz := TIMESTAMPTZ '2026-11-10 14:00 Europe/Prague';
    handover timestamptz := TIMESTAMPTZ '2026-11-10 19:00 Europe/Prague';
    shift_end timestamptz := TIMESTAMPTZ '2026-11-10 22:00 Europe/Prague';
BEGIN
    INSERT INTO block (code) VALUES ('A') RETURNING id INTO block_a;
    INSERT INTO block (code) VALUES ('B') RETURNING id INTO block_b;
    INSERT INTO employee (home_block_id, first_name, last_name)
        VALUES (block_a, 'Anna', 'Testova') RETURNING id INTO demo_employee;

    INSERT INTO shift (block_id, starts_at, ends_at)
        VALUES (block_a, shift_start, shift_end) RETURNING id INTO shift_a;
    INSERT INTO shift (block_id, starts_at, ends_at)
        VALUES (block_b, shift_start, shift_end) RETURNING id INTO shift_b;

    INSERT INTO shift_assignment (shift_id, employee_id, starts_at, ends_at)
        VALUES (shift_a, demo_employee, shift_start, handover),
               (shift_b, demo_employee, handover, shift_end);
END;
$$;
