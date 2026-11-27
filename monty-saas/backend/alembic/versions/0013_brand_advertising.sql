-- MON-AGENT brand advertising-- Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code.
: advertiser requests are moderated before publication.
CREATE TABLE IF NOT EXISTS brand_ad_campaigns (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  advertiser_name varchar(140) NOT NULL,
  brand_name varchar(120) NOT NULL,
  contact_email varchar(254) NOT NULL,
  website_url text NOT NULL DEFAULT '',
  creative_image_url text NOT NULL DEFAULT '',
  target_url text NOT NULL,
  placement varchar(32) NOT NULL DEFAULT 'HOME',
  country_code varchar(2) NOT NULL DEFAULT 'FR',
  city varchar(100) NOT NULL DEFAULT '',
  headline varchar(100) NOT NULL,
  description varchar(240) NOT NULL,
  campaign_budget_euro integer NOT NULL DEFAULT 0,
  status varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW',
  starts_at timestamptz,
  ends_at timestamptz,
  impressions bigint NOT NULL DEFAULT 0,
  clicks bigint NOT NULL DEFAULT 0,
  consent_at timestamptz NOT NULL DEFAULT now(),
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT brand_ad_campaigns_placement_check CHECK (placement IN ('HOME','SEARCH','CONTENT','PRO')),
  CONSTRAINT brand_ad_campaigns_country_check CHECK (country_code IN ('FR','PT','GB')),
  CONSTRAINT brand_ad_campaigns_budget_check CHECK (campaign_budget_euro >= 0),
  CONSTRAINT brand_ad_campaigns_status_check CHECK (status IN ('PENDING_REVIEW','APPROVED','REJECTED','PAUSED','COMPLETED'))
);
CREATE INDEX IF NOT EXISTS idx_brand_ads_public ON brand_ad_campaigns (placement, country_code, status, starts_at, ends_at);
CREATE INDEX IF NOT EXISTS idx_brand_ads_review ON brand_ad_campaigns (status, created_at DESC);
