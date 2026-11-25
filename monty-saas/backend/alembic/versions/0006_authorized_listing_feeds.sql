-- Monty SaaS — Migration 0006
-- Flux partenaires & syndication agences autorisés (XML / JSON / JSON-LD) conformes RGPD & robots.txt

CREATE TABLE IF NOT EXISTS authorized_listing_feeds (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  name varchar(120) NOT NULL,
  country_code varchar(2) NOT NULL DEFAULT 'FR' CHECK (country_code ~ '^[A-Z]{2}$'),
  publisher_type varchar(20) NOT NULL CHECK (publisher_type IN ('PARTICULIER', 'AGENCE')),
  feed_format varchar(16) NOT NULL CHECK (feed_format IN ('JSON', 'XML', 'JSON_LD')),
  feed_url text NOT NULL,
  agreement_confirmed boolean NOT NULL DEFAULT false,
  respect_robots_txt boolean NOT NULL DEFAULT true,
  auto_publish_verified_agency boolean NOT NULL DEFAULT false,
  last_synced_at timestamptz,
  last_status varchar(32) NOT NULL DEFAULT 'READY',
  total_ingested integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE authorized_listing_feeds ENABLE ROW LEVEL SECURITY;
ALTER TABLE authorized_listing_feeds FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_authorized_feeds ON authorized_listing_feeds;
CREATE POLICY tenant_isolation_authorized_feeds ON authorized_listing_feeds
  USING (agency_id::text = current_setting('app.current_agency_id', true));
