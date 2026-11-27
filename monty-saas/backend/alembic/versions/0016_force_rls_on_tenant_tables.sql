-- Force existing tenant RLS policies to apply to table owners as well.
-- Runtime connections must still use a non-superuser, non-BYPASSRLS role.
ALTER TABLE news_sources FORCE ROW LEVEL SECURITY;
ALTER TABLE news_articles FORCE ROW LEVEL SECURITY;

ALTER TABLE monty_ai_memories FORCE ROW LEVEL SECURITY;
ALTER TABLE monty_ai_conversations FORCE ROW LEVEL SECURITY;
ALTER TABLE monty_ai_pending_actions FORCE ROW LEVEL SECURITY;
ALTER TABLE monty_ai_audit_events FORCE ROW LEVEL SECURITY;

ALTER TABLE agency_module_flags FORCE ROW LEVEL SECURITY;
ALTER TABLE lead_keyword_rules FORCE ROW LEVEL SECURITY;

ALTER TABLE monty_price_index_configs FORCE ROW LEVEL SECURITY;
ALTER TABLE monty_price_index_history FORCE ROW LEVEL SECURITY;
