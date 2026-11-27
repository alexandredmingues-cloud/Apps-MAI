-- Migration 0012 : Extensions Lot 1-4 (seuil 6 niveaux Monty Price Index + commandes Pack Vendeur Stripe)
-- Strictement non destructive et réversible.

ALTER TABLE monty_price_index_configs
  ADD COLUMN IF NOT EXISTS excellent_deal_pct numeric(5, 2) NOT NULL DEFAULT -15.0;

CREATE TABLE IF NOT EXISTS seller_pack_orders (
  order_id varchar(96) PRIMARY KEY,
  dossier_id varchar(128) NOT NULL,
  property_id varchar(128) NOT NULL DEFAULT '',
  buyer_user_id varchar(128) NOT NULL DEFAULT '',
  buyer_email varchar(160) NOT NULL DEFAULT '',
  amount_euro integer NOT NULL DEFAULT 29,
  status varchar(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
  stripe_session_id varchar(180) NOT NULL DEFAULT '',
  paid_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_seller_pack_orders_dossier
  ON seller_pack_orders (dossier_id, buyer_user_id);
