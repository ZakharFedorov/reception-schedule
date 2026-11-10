-- Run once after demo.sql in the local demo database.
DO $$
DECLARE
    block_row record;
    admin_id bigint;
    demo_hash constant text := '$2b$12$VImatzL7tDtFpCi3Zfh/hOtrC8bc7A3EbL4sop2uF0umffgO0IRga';
BEGIN
    IF (SELECT count(*) FROM block WHERE code IN ('A', 'B')) <> 2 THEN
        RAISE EXCEPTION 'Load dev/demo.sql first';
    END IF;
    FOR block_row IN SELECT id, code FROM block WHERE code IN ('A', 'B') ORDER BY code LOOP
        INSERT INTO employee (home_block_id, first_name, last_name, block_admin)
            VALUES (block_row.id, 'Demo', 'Admin ' || block_row.code, true)
            RETURNING id INTO admin_id;
        INSERT INTO local_account (employee_id, login, password_hash)
            VALUES (admin_id, 'admin-' || lower(block_row.code) || '@example.test', demo_hash);
        INSERT INTO shift (block_id, starts_at, ends_at)
            VALUES (block_row.id, TIMESTAMPTZ '2026-11-11 14:00 Europe/Prague',
                    TIMESTAMPTZ '2026-11-11 22:00 Europe/Prague');
    END LOOP;
END;
$$;

SELECT e.id AS employee_id, e.first_name, b.code AS home_block
FROM employee e JOIN block b ON b.id = e.home_block_id
WHERE e.first_name = 'Anna' AND e.last_name = 'Testova';

SELECT s.id AS shift_id, b.code AS shift_block
FROM shift s JOIN block b ON b.id = s.block_id
WHERE s.starts_at = TIMESTAMPTZ '2026-11-11 14:00 Europe/Prague'
ORDER BY b.code;
