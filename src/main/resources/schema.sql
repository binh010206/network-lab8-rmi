CREATE TABLE IF NOT EXISTS sync_events (
  event_id VARCHAR(100) CONSTRAINT pk_sync_events PRIMARY KEY,
  version_no BIGINT NOT NULL CONSTRAINT ck_event_version CHECK (version_no >= 1),
  origin VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL,
  deleted SMALLINT DEFAULT 0 NOT NULL CONSTRAINT ck_event_deleted CHECK (deleted IN (0, 1)),
  student_id VARCHAR(40) NOT NULL CONSTRAINT ck_event_student CHECK (student_id ~ '^[A-Za-z0-9_-]{1,40}$'),
  full_name VARCHAR(100) NOT NULL CONSTRAINT ck_event_name CHECK (length(trim(full_name)) > 0),
  email VARCHAR(254) NOT NULL CONSTRAINT ck_event_email CHECK (email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'),
  major VARCHAR(100) NOT NULL CONSTRAINT ck_event_major CHECK (length(trim(major)) > 0),
  gpa NUMERIC NOT NULL CONSTRAINT ck_event_gpa CHECK (gpa BETWEEN 0 AND 4),
  acked SMALLINT DEFAULT 0 NOT NULL CONSTRAINT ck_event_acked CHECK (acked IN (0, 1))
);
-- statement
CREATE TABLE IF NOT EXISTS students (
  student_id VARCHAR(40) CONSTRAINT pk_students PRIMARY KEY,
  full_name VARCHAR(100) NOT NULL CONSTRAINT ck_student_name CHECK (length(trim(full_name)) > 0),
  email VARCHAR(254) NOT NULL CONSTRAINT ck_student_email CHECK (email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'),
  major VARCHAR(100) NOT NULL CONSTRAINT ck_student_major CHECK (length(trim(major)) > 0),
  gpa NUMERIC NOT NULL CONSTRAINT ck_student_gpa CHECK (gpa BETWEEN 0 AND 4),
  version_no BIGINT NOT NULL CONSTRAINT ck_student_version CHECK (version_no >= 1),
  origin VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL,
  deleted SMALLINT DEFAULT 0 NOT NULL CONSTRAINT ck_student_deleted CHECK (deleted IN (0, 1)),
  event_id VARCHAR(100) NOT NULL CONSTRAINT uq_student_event UNIQUE,
  CONSTRAINT ck_student_id CHECK (student_id ~ '^[A-Za-z0-9_-]{1,40}$'),
  CONSTRAINT fk_student_event FOREIGN KEY (event_id) REFERENCES sync_events(event_id)
);
-- statement
CREATE INDEX IF NOT EXISTS ix_events_pending ON sync_events(origin, acked);
