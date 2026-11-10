-- Private JSON-record store matching the current H2 records(kind,id,body) model.
-- This schema is intentionally not exposed through Supabase Data API.
CREATE SCHEMA IF NOT EXISTS zerdeai;
COMMENT ON SCHEMA zerdeai IS 'Private ZerdeAI server storage; do not expose through the Supabase Data API.';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'zerdeai_backend') THEN
    CREATE ROLE zerdeai_backend NOLOGIN;
  END IF;
END
$$;

REVOKE ALL ON SCHEMA zerdeai FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA zerdeai TO zerdeai_backend;

CREATE TABLE IF NOT EXISTS zerdeai.records (
  kind VARCHAR(30) NOT NULL,
  id VARCHAR(60) NOT NULL,
  body JSONB NOT NULL,
  CONSTRAINT records_pk PRIMARY KEY (kind, id),
  CONSTRAINT records_body_object CHECK (jsonb_typeof(body) = 'object'),
  CONSTRAINT records_body_id_matches CHECK (body ? 'id' AND body ->> 'id' = id)
);

ALTER TABLE zerdeai.records ENABLE ROW LEVEL SECURITY;
ALTER TABLE zerdeai.records FORCE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE zerdeai.records FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE zerdeai.records TO zerdeai_backend;

DROP POLICY IF EXISTS zerdeai_backend_all_records ON zerdeai.records;
CREATE POLICY zerdeai_backend_all_records
  ON zerdeai.records
  FOR ALL
  TO zerdeai_backend
  USING (true)
  WITH CHECK (true);
