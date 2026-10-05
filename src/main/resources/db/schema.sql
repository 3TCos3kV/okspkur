CREATE TABLE users (
    id            bigint PRIMARY KEY,
    login         text NOT NULL,
    password_hash text NOT NULL,
    full_name     text NOT NULL
);

CREATE TABLE specialists (
    id        bigint PRIMARY KEY,
    full_name text NOT NULL,
    specialty text NOT NULL
);

CREATE TABLE services (
    id           bigint PRIMARY KEY,
    name         text NOT NULL,
    specialty    text NOT NULL,
    duration_min int  NOT NULL CHECK (duration_min > 0)
);

CREATE TABLE slots (
    id            bigint PRIMARY KEY,
    specialist_id bigint      NOT NULL REFERENCES specialists,
    starts_at     timestamptz NOT NULL,
    ends_at       timestamptz NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE TABLE bookings (
    id           bigint PRIMARY KEY,
    slot_id      bigint      NOT NULL REFERENCES slots,
    service_id   bigint      NOT NULL REFERENCES services,
    client_id    bigint      NOT NULL REFERENCES users,
    status       text        NOT NULL CHECK (status IN ('active', 'cancelled')),
    created_at   timestamptz NOT NULL,
    cancelled_at timestamptz,
    CHECK ((status = 'cancelled') = (cancelled_at IS NOT NULL))
);

CREATE SEQUENCE bookings_id_seq OWNED BY bookings.id;
ALTER TABLE bookings ALTER COLUMN id SET DEFAULT nextval('bookings_id_seq');
