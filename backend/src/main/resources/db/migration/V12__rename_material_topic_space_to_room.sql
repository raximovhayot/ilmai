ALTER TABLE topics RENAME COLUMN space_id TO room_id;
ALTER TABLE materials RENAME COLUMN space_id TO room_id;

ALTER INDEX uk_topics_space_name_ci RENAME TO uk_topics_room_name_ci;
ALTER INDEX idx_topics_space_id RENAME TO idx_topics_room_id;
ALTER INDEX idx_materials_space_created RENAME TO idx_materials_room_created;

ALTER TABLE topics RENAME CONSTRAINT fk_topics_space_id TO fk_topics_room_id;
ALTER TABLE materials RENAME CONSTRAINT fk_materials_space_id TO fk_materials_room_id;
