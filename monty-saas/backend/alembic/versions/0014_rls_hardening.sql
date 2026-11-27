-- Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code.
-- Force RLS even for table owners and apply explicit policies to advertiser campaigns.
ALTER TABLE agency_memberships FORCE ROW LEVEL SECURITY;
ALTER TABLE agency_invites FORCE ROW LEVEL SECURITY;

ALTER TABLE brand_ad_campaigns ENABLE ROW LEVEL SECURITY;
ALTER TABLE brand_ad_campaigns FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS brand_ads_public_read_approved ON brand_ad_campaigns;
CREATE POLICY brand_ads_public_read_approved ON brand_ad_campaigns
  FOR SELECT USING (
    status = 'APPROVED'
    AND (starts_at IS NULL OR starts_at <= now())
    AND (ends_at IS NULL OR ends_at >= now())
  );

DROP POLICY IF EXISTS brand_ads_public_insert_pending ON brand_ad_campaigns;
CREATE POLICY brand_ads_public_insert_pending ON brand_ad_campaigns
  FOR INSERT WITH CHECK (status = 'PENDING_REVIEW' AND consent_at IS NOT NULL);

DROP POLICY IF EXISTS brand_ads_public_update_metrics ON brand_ad_campaigns;
CREATE POLICY brand_ads_public_update_metrics ON brand_ad_campaigns
  FOR UPDATE
  USING (status = 'APPROVED' AND (starts_at IS NULL OR starts_at <= now()) AND (ends_at IS NULL OR ends_at >= now()))
  WITH CHECK (status = 'APPROVED');

DROP POLICY IF EXISTS brand_ads_super_admin_all ON brand_ad_campaigns;
CREATE POLICY brand_ads_super_admin_all ON brand_ad_campaigns
  FOR ALL
  USING (current_setting('app.current_role', true) = 'SUPER_ADMIN')
  WITH CHECK (current_setting('app.current_role', true) = 'SUPER_ADMIN');

CREATE INDEX IF NOT EXISTS idx_brand_ads_public_active
  ON brand_ad_campaigns (placement, country_code, city, starts_at, ends_at)
  WHERE status = 'APPROVED';

-- Public tracking endpoints may change only one aggregate counter per request.
CREATE OR REPLACE FUNCTION guard_brand_ad_public_update() RETURNS trigger AS $func$
BEGIN
  IF current_setting('app.current_role', true) IS DISTINCT FROM 'SUPER_ADMIN' THEN
    IF (to_jsonb(NEW) - 'impressions' - 'clicks') IS DISTINCT FROM (to_jsonb(OLD) - 'impressions' - 'clicks')
       OR NEW.impressions < OLD.impressions OR NEW.clicks < OLD.clicks
       OR NEW.impressions > OLD.impressions + 1 OR NEW.clicks > OLD.clicks + 1
       OR (NEW.impressions - OLD.impressions) + (NEW.clicks - OLD.clicks) > 1 THEN
      RAISE EXCEPTION 'Public advertising endpoints may update aggregate metrics only';
    END IF;
  END IF;
  RETURN NEW;
END;
$func$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_guard_brand_ad_public_update ON brand_ad_campaigns;
CREATE TRIGGER trg_guard_brand_ad_public_update
  BEFORE UPDATE ON brand_ad_campaigns
  FOR EACH ROW EXECUTE FUNCTION guard_brand_ad_public_update();
