-- A LibertyBans 1.1.x database on HSQLDB (its default storage), table prefix libertybans_.
-- The DDL is what LibertyBans' Flyway migrations V1-V40 leave behind on HSQLDB (uuidtype UUID,
-- inettype VARBINARY(16), arbitrarybinarytype BLOB), with the columns that later migrations add
-- (punishments.track by V32, punishments.scope_id by V36) folded into the CREATE TABLE and the
-- scope value widened to 255 as 1.2's V42 does. The views (latest_names, simple_*, applicable_*,
-- strict_links) are left out: the importer reads the base tables.
-- Times are epoch seconds; "end" 0 is permanent. The console operator is the nil UUID. A player
-- victim stores the address 0.0.0.0, an address victim the nil UUID. Victim ids start at
-- Integer.MIN_VALUE (LibertyBans' victim_ids sequence).
-- The test clock is 2026-11-19T12:00:00Z (1795089600).

CREATE SEQUENCE "libertybans_punishment_ids" AS BIGINT START WITH 10 MINVALUE 1
    MAXVALUE 9223372036854775806 NO CYCLE;
CREATE SEQUENCE "libertybans_victim_ids" AS INT START WITH -2147483644 MINVALUE -2147483648
    MAXVALUE 2147483647 NO CYCLE;

CREATE TABLE "libertybans_revision" (
  "constant" CHARACTER VARYING(8) NOT NULL UNIQUE CHECK ("constant" = 'Constant'),
  "major" INT NOT NULL,
  "minor" INT NOT NULL
);
INSERT INTO "libertybans_revision" VALUES ('Constant', 0, 0);

CREATE TABLE "libertybans_names" (
  "uuid" UUID NOT NULL,
  "name" CHARACTER VARYING(16) NOT NULL,
  "lower_name" CHARACTER VARYING(16) GENERATED ALWAYS AS (LOWER("name")),
  "updated" BIGINT NOT NULL,
  CONSTRAINT "libertybans_uuid_name_uniqueness" UNIQUE ("uuid", "name")
);
CREATE INDEX "libertybans_name_index" ON "libertybans_names" ("lower_name");

CREATE TABLE "libertybans_addresses" (
  "uuid" UUID NOT NULL,
  "address" VARBINARY(16) NOT NULL,
  "updated" BIGINT NOT NULL,
  CONSTRAINT "libertybans_uuid_address_uniqueness" UNIQUE ("uuid", "address"),
  CONSTRAINT "libertybans_address_length" CHECK (OCTET_LENGTH("address") IN (4, 16))
);
CREATE INDEX "libertybans_address_index" ON "libertybans_addresses" ("address");

CREATE TABLE "libertybans_tracks" (
  "id" INT NOT NULL,
  "namespace" CHARACTER VARYING(64) NOT NULL,
  "value" CHARACTER VARYING(64) NOT NULL,
  CONSTRAINT "libertybans_track_id_uniqueness" UNIQUE ("id"),
  CONSTRAINT "libertybans_track_uniqueness" UNIQUE ("namespace", "value"),
  CONSTRAINT "libertybans_track_validity" CHECK ("namespace" <> '' AND "value" <> ''
    AND "namespace" NOT LIKE '%:%' AND "value" NOT LIKE '%:%')
);

CREATE TABLE "libertybans_scopes" (
  "id" INT NOT NULL,
  "type" SMALLINT NOT NULL,
  "value" CHARACTER VARYING(255) NOT NULL,
  CONSTRAINT "libertybans_scope_id_uniqueness" UNIQUE ("id"),
  CONSTRAINT "libertybans_scope_type_validity" CHECK ("type" IN (1, 2)),
  CONSTRAINT "libertybans_scope_data_uniqueness" UNIQUE ("type", "value")
);

CREATE TABLE "libertybans_punishments" (
  "id" BIGINT NOT NULL,
  "type" SMALLINT NOT NULL,
  "operator" UUID NOT NULL,
  "reason" CHARACTER VARYING(256) NOT NULL,
  "scope" CHARACTER VARYING(32) NOT NULL,
  "start" BIGINT NOT NULL,
  "end" BIGINT NOT NULL,
  "track" INT NULL,
  "scope_id" INT NULL,
  CONSTRAINT "libertybans_punishment_id_uniqueness" UNIQUE ("id"),
  CONSTRAINT "libertybans_punishment_type_validity" CHECK ("type" >= 0 AND "type" <= 3),
  CONSTRAINT "libertybans_punishment_duration_positivity" CHECK ("end" = 0 OR "end" > "start")
);
CREATE INDEX "libertybans_punishment_operator_index" ON "libertybans_punishments" ("operator");
CREATE INDEX "libertybans_punishment_start_index" ON "libertybans_punishments" ("start");
CREATE INDEX "libertybans_punishment_end_index" ON "libertybans_punishments" ("end");

CREATE TABLE "libertybans_victims" (
  "id" INT NOT NULL,
  "type" SMALLINT NOT NULL,
  "uuid" UUID NOT NULL,
  "address" VARBINARY(16) NOT NULL,
  CONSTRAINT "libertybans_victim_id_uniqueness" UNIQUE ("id"),
  CONSTRAINT "libertybans_victim_type_validity" CHECK ("type" >= 0 AND "type" <= 2),
  CONSTRAINT "libertybans_victim_uniqueness" UNIQUE ("type", "uuid", "address"),
  CONSTRAINT "libertybans_victim_address_length" CHECK (OCTET_LENGTH("address") IN (4, 16))
);
CREATE INDEX "libertybans_victim_uuid_index" ON "libertybans_victims" ("uuid");
CREATE INDEX "libertybans_victim_address_index" ON "libertybans_victims" ("address");

CREATE TABLE "libertybans_bans" (
  "id" BIGINT NOT NULL,
  "victim" INT NOT NULL,
  CONSTRAINT "libertybans_ban_id_uniqueness" UNIQUE ("id"),
  CONSTRAINT "libertybans_ban_id_validity" FOREIGN KEY ("id")
    REFERENCES "libertybans_punishments" ("id") ON DELETE CASCADE,
  CONSTRAINT "libertybans_ban_victim_uniqueness" UNIQUE ("victim"),
  CONSTRAINT "libertybans_ban_victim_validity" FOREIGN KEY ("victim")
    REFERENCES "libertybans_victims" ("id")
);

CREATE TABLE "libertybans_mutes" (
  "id" BIGINT NOT NULL,
  "victim" INT NOT NULL,
  CONSTRAINT "libertybans_mute_id_uniqueness" UNIQUE ("id"),
  CONSTRAINT "libertybans_mute_id_validity" FOREIGN KEY ("id")
    REFERENCES "libertybans_punishments" ("id") ON DELETE CASCADE,
  CONSTRAINT "libertybans_mute_victim_uniqueness" UNIQUE ("victim"),
  CONSTRAINT "libertybans_mute_victim_validity" FOREIGN KEY ("victim")
    REFERENCES "libertybans_victims" ("id")
);

CREATE TABLE "libertybans_warns" (
  "id" BIGINT NOT NULL,
  "victim" INT NOT NULL,
  CONSTRAINT "libertybans_warn_id_uniqueness" UNIQUE ("id"),
  CONSTRAINT "libertybans_warn_id_validity" FOREIGN KEY ("id")
    REFERENCES "libertybans_punishments" ("id") ON DELETE CASCADE,
  CONSTRAINT "libertybans_warn_victim_validity" FOREIGN KEY ("victim")
    REFERENCES "libertybans_victims" ("id")
);
CREATE INDEX "libertybans_warn_victim_index" ON "libertybans_warns" ("victim");

CREATE TABLE "libertybans_history" (
  "id" BIGINT NOT NULL,
  "victim" INT NOT NULL,
  CONSTRAINT "libertybans_history_id_uniqueness" UNIQUE ("id"),
  CONSTRAINT "libertybans_history_id_validity" FOREIGN KEY ("id")
    REFERENCES "libertybans_punishments" ("id") ON DELETE CASCADE,
  CONSTRAINT "libertybans_history_victim_validity" FOREIGN KEY ("victim")
    REFERENCES "libertybans_victims" ("id")
);
CREATE INDEX "libertybans_history_victim_index" ON "libertybans_history" ("victim");

CREATE TABLE "libertybans_messages" (
  "message" BLOB NOT NULL,
  "time" BIGINT NOT NULL
);
CREATE INDEX "libertybans_messages_time_index" ON "libertybans_messages" ("time");

CREATE TABLE "libertybans_address_whitelist" (
  "address" VARBINARY(16) NOT NULL,
  "whitelisted_on" BIGINT NOT NULL,
  "whitelisted_by" UUID NOT NULL,
  CONSTRAINT "libertybans_whitelist_address_uniqueness" UNIQUE ("address")
);

-- Players: Steve (8667ba71) was OldSteve, then Steve, at two addresses; Alex (ec561538) on
-- IPv6; the moderator (0f6b8d6a) renamed Moderator -> Mod. The operator 3c1f0e2a never joined.
INSERT INTO "libertybans_names" ("uuid", "name", "updated") VALUES
  ('8667ba71-b85a-4004-af54-457a9734eed7', 'OldSteve', 1704067200),
  ('8667ba71-b85a-4004-af54-457a9734eed7', 'Steve', 1748736000),
  ('ec561538-f3fd-461d-aff5-086b22154bce', 'Alex', 1748736000),
  ('0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Moderator', 1709251200),
  ('0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Mod', 1767225600);
INSERT INTO "libertybans_addresses" VALUES
  ('8667ba71-b85a-4004-af54-457a9734eed7', X'cb007107', 1704067200),
  ('8667ba71-b85a-4004-af54-457a9734eed7', X'c6336404', 1748736000),
  ('ec561538-f3fd-461d-aff5-086b22154bce', X'20010db8000000000000000000000001', 1748736000);

INSERT INTO "libertybans_scopes" VALUES
  (1, 1, 'lobby'),
  (2, 2, 'survival'),
  (3, 1, 'Main Hub!');

INSERT INTO "libertybans_tracks" VALUES (1, 'libertybans', 'hacking');

-- Victims: Steve (player), 203.0.113.9 (address), Alex + 2001:db8::1 (composite), Alex.
INSERT INTO "libertybans_victims" VALUES
  (-2147483648, 0, '8667ba71-b85a-4004-af54-457a9734eed7', X'00000000'),
  (-2147483647, 1, '00000000-0000-0000-0000-000000000000', X'cb007109'),
  (-2147483646, 2, 'ec561538-f3fd-461d-aff5-086b22154bce', X'20010db8000000000000000000000001'),
  (-2147483645, 0, 'ec561538-f3fd-461d-aff5-086b22154bce', X'00000000');

-- Types: 0 ban, 1 mute, 2 warn, 3 kick.
INSERT INTO "libertybans_punishments" VALUES
  -- Active permanent ban of Steve by the console, legacy colours, on an escalation track.
  (1, 0, '00000000-0000-0000-0000-000000000000', '&cHacking §lbadly', '', 1704067200, 0, 1, NULL),
  -- Active temporary mute of an address, by the moderator, on the server lobby.
  (2, 1, '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Spam', '', 1793491200, 1796083200, NULL, 1),
  -- An expired ban of Alex, already swept from the bans table.
  (3, 0, '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Alting', '', 1704067200, 1709251200, NULL, NULL),
  -- A revoked permanent composite ban (the longest reason LibertyBans takes) by an operator
  -- without a name, in the category survival.
  (4, 0, '3c1f0e2a-5b6d-4e7f-8a9b-0c1d2e3f4a5b', 'Repeatedly joining on alternate accounts to evade a ban on the main account. Evidence: matching address, matching client brand, same chat patterns and the same build style on survival. Appeal on the forum if you think this is a mistake, and name your main.', '', 1748736000, 0, NULL, 2),
  -- A revoked temporary mute of Steve, before its end.
  (5, 1, '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Toxic', '', 1748736000, 1798761600, NULL, NULL),
  -- An active permanent warn of Steve.
  (6, 2, '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Language', '', 1748736000, 0, NULL, NULL),
  -- A warn of Alex past its end, not swept yet.
  (7, 2, '00000000-0000-0000-0000-000000000000', 'Old warn', '', 1704067200, 1709251200, NULL, NULL),
  -- A kick of Alex (history only).
  (8, 3, '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Go away', '', 1749340800, 0, NULL, NULL),
  -- A ban in a scope that isn't a valid server name here: reported, not imported.
  (9, 0, '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Bad scope', '', 1749340800, 0, NULL, 3);

INSERT INTO "libertybans_bans" VALUES (1, -2147483648), (9, -2147483647);
INSERT INTO "libertybans_mutes" VALUES (2, -2147483647);
INSERT INTO "libertybans_warns" VALUES (6, -2147483648), (7, -2147483645);
INSERT INTO "libertybans_history" VALUES
  (1, -2147483648),
  (2, -2147483647),
  (3, -2147483645),
  (4, -2147483646),
  (5, -2147483648),
  (6, -2147483648),
  (7, -2147483645),
  (8, -2147483645),
  (9, -2147483647);
