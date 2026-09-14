-- Run once when upgrading a database that already contains Outbox v2.
ALTER TABLE tb_outbox_event
    ADD COLUMN locked_by varchar(64) CHARACTER SET ascii COLLATE ascii_general_ci NULL AFTER sent_time,
    ADD COLUMN locked_until timestamp NULL DEFAULT NULL AFTER locked_by,
    ADD INDEX idx_outbox_lease(status, locked_until);
