CREATE TABLE IF NOT EXISTS wards (
    ward_id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    time_zone VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS ward_memberships (
    ward_id UUID NOT NULL REFERENCES wards(ward_id),
    subject VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('WARD_ADMIN', 'WARD_MEMBER')),
    can_view_ward_calendar BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (ward_id, subject, role)
);

CREATE TABLE IF NOT EXISTS nurses (
    nurse_id UUID PRIMARY KEY,
    ward_id UUID NOT NULL REFERENCES wards(ward_id),
    subject VARCHAR(255),
    display_name VARCHAR(100) NOT NULL,
    qualifications VARCHAR(2000) NOT NULL DEFAULT '[]',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (ward_id, subject)
);

CREATE TABLE IF NOT EXISTS shift_types (
    shift_type_id UUID PRIMARY KEY,
    ward_id UUID NOT NULL REFERENCES wards(ward_id),
    code VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('DAY', 'NIGHT', 'OTHER')),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    spans_midnight BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (ward_id, code)
);

CREATE TABLE IF NOT EXISTS rosters (
    roster_id UUID PRIMARY KEY,
    ward_id UUID NOT NULL REFERENCES wards(ward_id),
    roster_month VARCHAR(7) NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    revision INT NOT NULL CHECK (revision >= 1),
    published_at VARCHAR(40),
    confirmed_by VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS ix_rosters_ward_month ON rosters(ward_id, roster_month);

CREATE TABLE IF NOT EXISTS roster_slots (
    slot_id UUID PRIMARY KEY,
    roster_id UUID NOT NULL REFERENCES rosters(roster_id),
    shift_date DATE NOT NULL,
    shift_type_id UUID NOT NULL REFERENCES shift_types(shift_type_id),
    starts_at VARCHAR(40) NOT NULL,
    ends_at VARCHAR(40) NOT NULL,
    required_qualifications VARCHAR(2000) NOT NULL DEFAULT '[]',
    nurse_id UUID REFERENCES nurses(nurse_id),
    locked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS ix_roster_slots_roster ON roster_slots(roster_id);

CREATE TABLE IF NOT EXISTS approved_absences (
    absence_id UUID PRIMARY KEY,
    roster_id UUID NOT NULL REFERENCES rosters(roster_id),
    nurse_id UUID NOT NULL REFERENCES nurses(nurse_id),
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('APPROVED_LEAVE', 'APPROVED_HALF_DAY')),
    starts_at VARCHAR(40) NOT NULL,
    ends_at VARCHAR(40) NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_approved_absences_roster ON approved_absences(roster_id);

CREATE TABLE IF NOT EXISTS mutation_replays (
    idempotency_key UUID PRIMARY KEY,
    operation VARCHAR(80) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_json CLOB NOT NULL
);

CREATE TABLE IF NOT EXISTS generation_jobs (
    job_id UUID PRIMARY KEY,
    roster_id UUID NOT NULL REFERENCES rosters(roster_id),
    requested_by VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_revision INT NOT NULL,
    result_revision INT,
    preserve_locked BOOLEAN NOT NULL,
    time_limit_seconds INT NOT NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    staffing_json CLOB,
    diagnostics_json CLOB,
    created_at VARCHAR(40) NOT NULL,
    finished_at VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS ix_generation_jobs_roster ON generation_jobs(roster_id, status);
