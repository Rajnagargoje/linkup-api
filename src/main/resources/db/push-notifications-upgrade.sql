-- PostgreSQL: run once before deploying this update, including when DDL_AUTO=update.
-- Extends the existing notification schema; does not change user/chat records.
BEGIN;

ALTER TABLE app_notifications ADD COLUMN IF NOT EXISTS silent boolean NOT NULL DEFAULT false;
ALTER TABLE notification_preferences ADD COLUMN IF NOT EXISTS support_replies boolean NOT NULL DEFAULT true;
ALTER TABLE push_devices ADD COLUMN IF NOT EXISTS auth_session_id varchar(36);
ALTER TABLE push_devices ADD COLUMN IF NOT EXISTS installation_id varchar(36);

CREATE TABLE IF NOT EXISTS push_receipts (
    id varchar(96) PRIMARY KEY,
    created_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_push_receipt_created ON push_receipts(created_at);

-- Hibernate does not reliably expand an existing enum CHECK during ddl-auto=update.
-- Find the old notification-type check even if PostgreSQL assigned a different name.
DO $$
DECLARE notification_check record;
BEGIN
    FOR notification_check IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'app_notifications'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%FRIEND_REQUEST%'
          AND pg_get_constraintdef(oid) LIKE '%REPORT_UPDATE%'
    LOOP
        EXECUTE format('ALTER TABLE app_notifications DROP CONSTRAINT %I', notification_check.conname);
    END LOOP;
END $$;
ALTER TABLE app_notifications ADD CONSTRAINT app_notifications_type_check
    CHECK (type IN ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'MESSAGE', 'REPORT_UPDATE', 'SUPPORT_REPLY', 'SYSTEM'));

COMMIT;
