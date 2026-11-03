CREATE TABLE IF NOT EXISTS wards (
    ward_id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    time_zone VARCHAR(100) NOT NULL
);

-- A single locked row makes demo bootstrap a one-time operation across app instances.
CREATE TABLE IF NOT EXISTS bootstrap_state (
    state_id INT PRIMARY KEY CHECK (state_id = 1),
    consumed BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO bootstrap_state(state_id, consumed)
SELECT 1, FALSE WHERE NOT EXISTS (SELECT 1 FROM bootstrap_state WHERE state_id = 1);

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
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('DAY', 'EVENING', 'NIGHT', 'OTHER')),
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

-- ── Identity: Kakao OIDC users and NurseFlow sessions ──
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('KAKAO')),
    provider_subject VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    last_login_at VARCHAR(40) NOT NULL,
    UNIQUE (provider, provider_subject)
);

CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    token_id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(user_id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at VARCHAR(40) NOT NULL,
    expires_at VARCHAR(40) NOT NULL,
    replaced_at VARCHAR(40),
    revoked_at VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS ix_auth_refresh_tokens_family ON auth_refresh_tokens(family_id);

-- ── Audit trail for ward access changes (no tokens, leave reasons or health data) ──
CREATE TABLE IF NOT EXISTS audit_events (
    event_id UUID PRIMARY KEY,
    ward_id UUID REFERENCES wards(ward_id),
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_id VARCHAR(255),
    created_at VARCHAR(40) NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_audit_events_ward ON audit_events(ward_id, created_at);

-- ── Ward invitations: single-use, expiring, revocable. Only the token hash is stored. ──
CREATE TABLE IF NOT EXISTS ward_invitations (
    invitation_id UUID PRIMARY KEY,
    ward_id UUID NOT NULL REFERENCES wards(ward_id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('WARD_ADMIN', 'WARD_MEMBER')),
    nurse_id UUID REFERENCES nurses(nurse_id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_by VARCHAR(255) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    expires_at VARCHAR(40) NOT NULL,
    revoked_at VARCHAR(40),
    redeemed_at VARCHAR(40),
    redeemed_by VARCHAR(255),
    CHECK (role = 'WARD_MEMBER' OR nurse_id IS NULL)
);
CREATE INDEX IF NOT EXISTS ix_ward_invitations_ward ON ward_invitations(ward_id, created_at);

-- ── Teams, career policy and nurse career profiles ──
CREATE TABLE IF NOT EXISTS ward_teams (
    team_id UUID PRIMARY KEY,
    ward_id UUID NOT NULL REFERENCES wards(ward_id),
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS ix_ward_teams_ward ON ward_teams(ward_id, sort_order);

-- Absent row = defaults (12/36/72/120 months, ranks 팀장·과장·신규). Values are ward settings, not code.
CREATE TABLE IF NOT EXISTS ward_career_policies (
    ward_id UUID PRIMARY KEY REFERENCES wards(ward_id),
    stage_threshold_months VARCHAR(200) NOT NULL,
    ranks VARCHAR(2000) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    updated_at VARCHAR(40) NOT NULL
);

-- Submitted values come from the nurse; confirmed_* are the admin-checked copy used for scoring.
ALTER TABLE nurses ADD COLUMN IF NOT EXISTS team_id UUID REFERENCES ward_teams(team_id);
ALTER TABLE nurses ADD COLUMN IF NOT EXISTS career_started_on VARCHAR(7);
ALTER TABLE nurses ADD COLUMN IF NOT EXISTS rank_name VARCHAR(30);
ALTER TABLE nurses ADD COLUMN IF NOT EXISTS profile_submitted_at VARCHAR(40);
ALTER TABLE nurses ADD COLUMN IF NOT EXISTS confirmed_career_started_on VARCHAR(7);
ALTER TABLE nurses ADD COLUMN IF NOT EXISTS confirmed_rank_name VARCHAR(30);
ALTER TABLE nurses ADD COLUMN IF NOT EXISTS profile_confirmed_at VARCHAR(40);
ALTER TABLE nurses ADD COLUMN IF NOT EXISTS profile_confirmed_by VARCHAR(255);
-- 병원 → 병동 소속 기반. 기존 H2 파일 DB에서도 매 기동 시 반복 실행할 수 있는 변경만 사용한다.
-- CREATE TABLE IF NOT EXISTS는 기존 wards 테이블을 갱신하지 않으므로 열·제약은 ALTER ... IF NOT EXISTS로 추가한다.
CREATE TABLE IF NOT EXISTS hospitals (
    hospital_id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    time_zone VARCHAR(100) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    CONSTRAINT uq_hospitals_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS hospital_memberships (
    hospital_id UUID NOT NULL REFERENCES hospitals(hospital_id),
    subject VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('HOSPITAL_ADMIN')),
    granted_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (hospital_id, subject, role)
);
CREATE INDEX IF NOT EXISTS ix_hospital_memberships_subject ON hospital_memberships(subject);

ALTER TABLE wards ADD COLUMN IF NOT EXISTS hospital_id UUID;
ALTER TABLE wards ADD COLUMN IF NOT EXISTS created_at VARCHAR(40);
ALTER TABLE wards ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE wards ADD CONSTRAINT IF NOT EXISTS fk_wards_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_wards_hospital_name ON wards(hospital_id, name);
