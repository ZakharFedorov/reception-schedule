ALTER TABLE employee
    ADD COLUMN block_admin boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX one_admin_per_block
    ON employee (home_block_id) WHERE block_admin;

CREATE TABLE local_account (
    employee_id bigint PRIMARY KEY REFERENCES employee (id),
    login text NOT NULL UNIQUE,
    password_hash text NOT NULL
);

-- One shift represents one reception post. Adjacent assignments are valid.
ALTER TABLE shift_assignment
    ADD CONSTRAINT shift_coverage_does_not_overlap
    EXCLUDE USING gist (
        shift_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    );

-- Existing V1 data must satisfy the same bounds as new assignments.
DO $$
BEGIN
    IF EXISTS (
        SELECT FROM shift_assignment a JOIN shift s ON s.id = a.shift_id
        WHERE a.starts_at < s.starts_at OR a.ends_at > s.ends_at
    ) THEN
        RAISE EXCEPTION 'Existing assignment falls outside its shift'
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE FUNCTION check_assignment_bounds() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_shift shift%ROWTYPE;
BEGIN
    SELECT * INTO STRICT parent_shift FROM shift WHERE id = NEW.shift_id FOR SHARE;
    IF NEW.starts_at < parent_shift.starts_at OR NEW.ends_at > parent_shift.ends_at THEN
        RAISE EXCEPTION 'Assignment falls outside its shift' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER assignment_within_shift
    BEFORE INSERT OR UPDATE OF shift_id, starts_at, ends_at ON shift_assignment
    FOR EACH ROW EXECUTE FUNCTION check_assignment_bounds();

CREATE FUNCTION check_shift_bounds() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT FROM shift_assignment WHERE shift_id = NEW.id
        AND (starts_at < NEW.starts_at OR ends_at > NEW.ends_at)
    ) THEN
        RAISE EXCEPTION 'New shift bounds exclude an existing assignment' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER shift_contains_assignments
    AFTER UPDATE OF starts_at, ends_at ON shift
    FOR EACH ROW EXECUTE FUNCTION check_shift_bounds();
