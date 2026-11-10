CREATE TABLE block (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code varchar(1) NOT NULL UNIQUE CHECK (code IN ('A', 'B', 'C', 'D', 'E', 'F'))
);

CREATE TABLE employee (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    home_block_id bigint NOT NULL REFERENCES block (id),
    first_name text NOT NULL,
    last_name text NOT NULL
);

CREATE TABLE shift (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    block_id bigint NOT NULL REFERENCES block (id),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    CHECK (starts_at < ends_at)
);

CREATE INDEX shift_block_start_idx ON shift (block_id, starts_at);

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE shift_assignment (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shift_id bigint NOT NULL REFERENCES shift (id),
    employee_id bigint NOT NULL REFERENCES employee (id),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    CHECK (starts_at < ends_at),
    CONSTRAINT employee_assignments_do_not_overlap
        EXCLUDE USING gist (
            employee_id WITH =,
            tstzrange(starts_at, ends_at, '[)') WITH &&
        )
);

CREATE INDEX shift_assignment_shift_idx ON shift_assignment (shift_id);
