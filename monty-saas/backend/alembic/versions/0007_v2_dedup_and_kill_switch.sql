-- Monty SaaS — Migration 0007
-- Déduplication persistante des annonces ingérées via flux autorisés + état persistant du kill-switch agents IA

CREATE TABLE IF NOT EXISTS authorized_feed_items (
  feed_id uuid NOT NULL REFERENCES authorized_listing_feeds(id) ON DELETE CASCADE,
  external_id varchar(160) NOT NULL,
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  property_id uuid NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
  ingested_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (feed_id, external_id)
);

ALTER TABLE authorized_feed_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE authorized_feed_items FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_authorized_feed_items ON authorized_feed_items;
CREATE POLICY tenant_isolation_authorized_feed_items ON authorized_feed_items
  USING (agency_id::text = current_setting('app.current_agency_id', true));

CREATE TABLE IF NOT EXISTS agent_governance_state (
  scope_key varchar(64) PRIMARY KEY,
  kill_switch_enabled boolean NOT NULL DEFAULT false,
  kill_switch_reason text NOT NULL DEFAULT '',
  updated_by_user_id text NOT NULL DEFAULT '',
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO agent_governance_state (scope_key, kill_switch_enabled, kill_switch_reason)
VALUES ('global', false, '')
ON CONFLICT (scope_key) DO NOTHING;
