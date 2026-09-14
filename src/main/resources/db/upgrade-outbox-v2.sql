-- Run once only when upgrading an existing LocalHub database created before Outbox v2.
ALTER TABLE tb_outbox_event
    ADD COLUMN event_id varchar(36) CHARACTER SET ascii COLLATE ascii_general_ci NULL AFTER id,
    ADD COLUMN version bigint NOT NULL DEFAULT 0 AFTER last_error,
    ADD COLUMN sent_time timestamp NULL DEFAULT NULL AFTER version;

UPDATE tb_outbox_event SET event_id = UUID() WHERE event_id IS NULL;
ALTER TABLE tb_outbox_event MODIFY event_id varchar(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL;
CREATE UNIQUE INDEX uk_outbox_event_id ON tb_outbox_event(event_id);
