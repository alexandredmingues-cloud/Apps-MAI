-- Payment and entitlement tables are intentionally not agency-scoped.
-- Access is restricted to the payment repository's transaction-local capability
-- or to the exact RNIC user/pass token used for entitlement checks.
ALTER TABLE seller_pack_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE seller_pack_orders FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS payment_processor_only_seller_pack_orders ON seller_pack_orders;
CREATE POLICY payment_processor_only_seller_pack_orders ON seller_pack_orders
  USING (current_setting('app.payment_processor', true) = 'on')
  WITH CHECK (current_setting('app.payment_processor', true) = 'on');

ALTER TABLE rnic_access_passes ENABLE ROW LEVEL SECURITY;
ALTER TABLE rnic_access_passes FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rnic_pass_owner_or_payment_processor ON rnic_access_passes;
CREATE POLICY rnic_pass_owner_or_payment_processor ON rnic_access_passes
  USING (
    current_setting('app.payment_processor', true) = 'on'
    OR (
      user_id IS NOT NULL
      AND user_id::text = NULLIF(current_setting('app.current_user_id', true), '')
    )
    OR pass_token = NULLIF(current_setting('app.rnic_pass_token', true), '')
  )
  WITH CHECK (current_setting('app.payment_processor', true) = 'on');
