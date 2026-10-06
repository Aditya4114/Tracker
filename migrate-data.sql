-- ====================================================================
-- Job Tracker: Zero-Data-Loss Migration Script
-- Moves existing data from the legacy 'jobtracker' database into
-- the respective isolated microservice databases.
-- ====================================================================

-- 1. Ensure target databases exist
SELECT 'CREATE DATABASE tracker_auth' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tracker_auth')\gexec
SELECT 'CREATE DATABASE tracker_ingestion' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tracker_ingestion')\gexec
SELECT 'CREATE DATABASE tracker_intelligence' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tracker_intelligence')\gexec
SELECT 'CREATE DATABASE tracker_core' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tracker_core')\gexec

-- 2. Migrate Auth Service Data (Users)
\connect tracker_auth
CREATE TABLE IF NOT EXISTS users (LIKE jobtracker.public.users INCLUDING ALL);
INSERT INTO users SELECT * FROM jobtracker.public.users ON CONFLICT DO NOTHING;

-- 3. Migrate Ingestion Service Data (Daily Sync Logs)
\connect tracker_ingestion
CREATE TABLE IF NOT EXISTS daily_sync_logs (LIKE jobtracker.public.daily_sync_logs INCLUDING ALL);
INSERT INTO daily_sync_logs SELECT * FROM jobtracker.public.daily_sync_logs ON CONFLICT DO NOTHING;

-- 4. Migrate Intelligence Service Data (Processed, Pending Reviews, Model State)
\connect tracker_intelligence
CREATE TABLE IF NOT EXISTS processed_emails (LIKE jobtracker.public.processed_emails INCLUDING ALL);
INSERT INTO processed_emails SELECT * FROM jobtracker.public.processed_emails ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS pending_review_emails (LIKE jobtracker.public.pending_review_emails INCLUDING ALL);
INSERT INTO pending_review_emails SELECT * FROM jobtracker.public.pending_review_emails ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS global_model_state (LIKE jobtracker.public.global_model_state INCLUDING ALL);
INSERT INTO global_model_state SELECT * FROM jobtracker.public.global_model_state ON CONFLICT DO NOTHING;

-- 5. Migrate Core Service Data (Job Applications & Timeline Events)
\connect tracker_core
CREATE TABLE IF NOT EXISTS job_applications (LIKE jobtracker.public.job_applications INCLUDING ALL);
INSERT INTO job_applications SELECT * FROM jobtracker.public.job_applications ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS application_events (LIKE jobtracker.public.application_events INCLUDING ALL);
INSERT INTO application_events SELECT * FROM jobtracker.public.application_events ON CONFLICT DO NOTHING;
