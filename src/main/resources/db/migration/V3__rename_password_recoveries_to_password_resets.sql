ALTER TABLE password_recoveries RENAME TO password_resets;

ALTER INDEX password_recoveries_user_idx RENAME TO password_resets_user_idx;
