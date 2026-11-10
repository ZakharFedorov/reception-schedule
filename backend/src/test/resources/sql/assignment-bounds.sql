-- Run in a disposable test schema after Flyway migrations.
DO $test$
DECLARE
    home bigint;
    worker bigint;
    night_shift bigint;
    assignment bigint;
    shift_start timestamptz := '2026-11-30 22:00 Europe/Prague';
    shift_end timestamptz := '2026-12-01 06:00 Europe/Prague';
    handover timestamptz := '2026-12-01 02:00 Europe/Prague';
BEGIN
    INSERT INTO block (code) VALUES ('C') ON CONFLICT (code) DO NOTHING;
    SELECT id INTO STRICT home FROM block WHERE code = 'C';
    INSERT INTO employee (home_block_id, first_name, last_name)
        VALUES (home, 'Test', 'Worker') RETURNING id INTO worker;
    INSERT INTO shift (block_id, starts_at, ends_at)
        VALUES (home, shift_start, shift_end) RETURNING id INTO night_shift;

    -- Both endpoints can coincide with the shift boundaries.
    INSERT INTO shift_assignment (shift_id, employee_id, starts_at, ends_at)
        VALUES (night_shift, worker, shift_start, shift_end) RETURNING id INTO assignment;
    UPDATE shift_assignment SET starts_at = handover WHERE id = assignment;

    BEGIN
        UPDATE shift_assignment SET starts_at = shift_start - interval '1 minute' WHERE id = assignment;
        RAISE EXCEPTION 'Assignment beginning before the shift was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO shift_assignment (shift_id, employee_id, starts_at, ends_at)
            VALUES (night_shift, worker, shift_end, shift_end + interval '1 minute');
        RAISE EXCEPTION 'Assignment ending after the shift was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE shift SET ends_at = shift_end - interval '1 minute' WHERE id = night_shift;
        RAISE EXCEPTION 'Shortening the shift past an assignment was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE shift SET starts_at = handover + interval '1 minute' WHERE id = night_shift;
        RAISE EXCEPTION 'Moving the shift start past an assignment was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    -- Shortening to the exact remaining assignment is valid.
    UPDATE shift SET starts_at = handover WHERE id = night_shift;
    IF NOT EXISTS (
        SELECT FROM shift s JOIN shift_assignment a ON a.shift_id = s.id
        WHERE s.id = night_shift AND a.id = assignment AND s.starts_at = handover
          AND a.starts_at = handover AND s.ends_at = shift_end AND a.ends_at = shift_end
    ) THEN
        RAISE EXCEPTION 'Unexpected bounds after rejected changes';
    END IF;
END;
$test$;
