-- Do not silently delete or rewrite historical APK releases.
-- Stop the migration with a diagnostic if duplicate Android build numbers already exist.
DO $$
DECLARE
    duplicate_versions text;
BEGIN
    SELECT string_agg(
        format('package_id=%s version_code=%s count=%s', package_id, version_code, release_count),
        '; '
    )
    INTO duplicate_versions
    FROM (
        SELECT package_id, version_code, count(*) AS release_count
        FROM mobile_app_releases
        GROUP BY package_id, version_code
        HAVING count(*) > 1
    ) duplicates;

    IF duplicate_versions IS NOT NULL THEN
        RAISE EXCEPTION
            'Migration 0018 blocked: duplicate mobile release versions must be reconciled manually before adding uniqueness: %',
            duplicate_versions;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mobile_app_releases_package_version
    ON mobile_app_releases (package_id, version_code);
