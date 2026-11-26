-- LiteBans tables as LiteBans creates them on MySQL/MariaDB (run on H2 in MySQL mode), for
-- LiteBansSourceTest. Times are epoch milliseconds; removed_by_date and history.date are
-- timestamps. 1893456000000 is 2030-01-01, after the tests' clock (2026-11-19).
-- LiteBans declares template TINYINT UNSIGNED, which H2 has no room for: SMALLINT here.
CREATE TABLE litebans_bans (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36),
    ip VARCHAR(45),
    reason VARCHAR(2048) NOT NULL,
    banned_by_uuid VARCHAR(36) NOT NULL,
    banned_by_name VARCHAR(128),
    removed_by_uuid VARCHAR(36),
    removed_by_name VARCHAR(128),
    removed_by_reason VARCHAR(2048),
    removed_by_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    time BIGINT NOT NULL,
    until BIGINT NOT NULL,
    template SMALLINT NOT NULL DEFAULT 255,
    server_scope VARCHAR(32),
    server_origin VARCHAR(32),
    silent BIT(1) NOT NULL,
    ipban BIT(1) NOT NULL,
    ipban_wildcard BIT(1) NOT NULL DEFAULT 0,
    active BIT(1) NOT NULL
);
CREATE TABLE litebans_mutes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36),
    ip VARCHAR(45),
    reason VARCHAR(2048) NOT NULL,
    banned_by_uuid VARCHAR(36) NOT NULL,
    banned_by_name VARCHAR(128),
    removed_by_uuid VARCHAR(36),
    removed_by_name VARCHAR(128),
    removed_by_reason VARCHAR(2048),
    removed_by_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    time BIGINT NOT NULL,
    until BIGINT NOT NULL,
    template SMALLINT NOT NULL DEFAULT 255,
    server_scope VARCHAR(32),
    server_origin VARCHAR(32),
    silent BIT(1) NOT NULL,
    ipban BIT(1) NOT NULL,
    ipban_wildcard BIT(1) NOT NULL DEFAULT 0,
    active BIT(1) NOT NULL
);
CREATE TABLE litebans_warnings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36),
    ip VARCHAR(45),
    reason VARCHAR(2048) NOT NULL,
    banned_by_uuid VARCHAR(36) NOT NULL,
    banned_by_name VARCHAR(128),
    removed_by_uuid VARCHAR(36),
    removed_by_name VARCHAR(128),
    removed_by_reason VARCHAR(2048),
    removed_by_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    time BIGINT NOT NULL,
    until BIGINT NOT NULL,
    template SMALLINT NOT NULL DEFAULT 255,
    server_scope VARCHAR(32),
    server_origin VARCHAR(32),
    silent BIT(1) NOT NULL,
    ipban BIT(1) NOT NULL,
    ipban_wildcard BIT(1) NOT NULL DEFAULT 0,
    active BIT(1) NOT NULL,
    warned BIT(1) NOT NULL DEFAULT 0
);
CREATE TABLE litebans_kicks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36),
    ip VARCHAR(45),
    reason VARCHAR(2048) NOT NULL,
    banned_by_uuid VARCHAR(36) NOT NULL,
    banned_by_name VARCHAR(128),
    time BIGINT NOT NULL,
    until BIGINT NOT NULL,
    template SMALLINT NOT NULL DEFAULT 255,
    server_scope VARCHAR(32),
    server_origin VARCHAR(32),
    silent BIT(1) NOT NULL,
    ipban BIT(1) NOT NULL,
    ipban_wildcard BIT(1) NOT NULL DEFAULT 0,
    active BIT(1) NOT NULL
);
CREATE TABLE litebans_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    name VARCHAR(16),
    uuid VARCHAR(36),
    ip VARCHAR(45)
);

-- Steve 8667ba71-..., Alex ec561538-..., Mod 0f6b8d6a-..., Herobrine 3f0d2a3c-...
INSERT INTO litebans_history (id, date, name, uuid, ip) VALUES
(1, '2024-01-01 10:00:00', 'Steve', '8667ba71-b85a-4004-af54-457a9734eed7', '203.0.113.5'),
(2, '2024-02-01 10:00:00', 'Steve', '8667ba71-b85a-4004-af54-457a9734eed7', '203.0.113.6'),
(3, '2024-01-05 10:00:00', 'Alex', 'ec561538-f3fd-461d-aff5-086b22154bce', '#offline#'),
(4, '2024-01-06 10:00:00', 'Alex', 'ec561538-f3fd-461d-aff5-086b22154bce', '198.51.100.7'),
(5, '2024-01-01 09:00:00', 'CONSOLE', 'CONSOLE', '#'),
(6, '2024-03-01 10:00:00', '', '8667ba71-b85a-4004-af54-457a9734eed7', '203.0.113.9'),
(7, '2024-01-07 10:00:00', 'Herobrine', '3f0d2a3c-1b5e-4c3a-9d6e-2a1b3c4d5e6f', '#undefined#'),
(8, '2024-01-08 10:00:00', 'Broken', 'garbage', '203.0.113.200');

INSERT INTO litebans_bans (id, uuid, ip, reason, banned_by_uuid, banned_by_name,
    removed_by_uuid, removed_by_name, removed_by_reason, removed_by_date, time, until,
    template, server_scope, server_origin, silent, ipban, ipban_wildcard, active) VALUES
-- 1: active permanent player ban by a player; the ip is only Steve's last address.
(1, '8667ba71-b85a-4004-af54-457a9734eed7', '203.0.113.6', '&cHacking &lfly',
    '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Mod', NULL, NULL, NULL, '2024-04-01 10:00:00',
    1711965600000, -1, 255, '*', 'lobby', 0, 0, 0, 1),
-- 2: active temporary IP ban of Alex by the console, scoped and silent: composite.
(2, 'ec561538-f3fd-461d-aff5-086b22154bce', '198.51.100.7', 'Alt abuse', 'CONSOLE',
    'Console', NULL, NULL, NULL, '2024-04-02 10:00:00', 1712052000000, 1893456000000, 255,
    'kitpvp', 'kitpvp', 1, 1, 0, 1),
-- 3: IP ban of an address nobody joined from, a name in banned_by_uuid.
(3, '#offline#', '198.51.100.8', 'VPN', 'Notch', 'Notch', NULL, NULL, NULL,
    '2024-04-03 10:00:00', 1712138400000, 0, 255, NULL, NULL, 0, 1, 0, 1),
-- 4: wildcard IP ban, expressible as 10.0.0.0/16.
(4, '#offline#', '10.0.*.*', 'Botnet', 'CONSOLE', 'Console', NULL, NULL, NULL,
    '2024-04-04 10:00:00', 1712224800000, 0, 255, '*', NULL, 0, 1, 1, 1),
-- 5: wildcard that isn't a CIDR range: a failure.
(5, '#offline#', '10.*.3.*', 'Odd', 'CONSOLE', 'Console', NULL, NULL, NULL,
    '2024-04-05 10:00:00', 1712311200000, 0, 255, '*', NULL, 0, 1, 1, 1),
-- 6: ran out: LiteBans marked it #expired.
(6, '8667ba71-b85a-4004-af54-457a9734eed7', '203.0.113.5', 'Spam', 'CONSOLE', 'Console',
    NULL, '#expired', NULL, '2024-01-03 12:00:00', 1704189600000, 1704276000000, 255, '*',
    NULL, 0, 0, 0, 0),
-- 7: revoked by Mod, with a reason.
(7, 'ec561538-f3fd-461d-aff5-086b22154bce', NULL, 'Griefing',
    '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Mod', '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b',
    'Mod', 'Appeal accepted', '2024-01-10 12:00:00', 1704801600000, 1893456000000, 255, '*',
    NULL, 0, 0, 0, 0),
-- 8: revoked by the console, permanent.
(8, '3f0d2a3c-1b5e-4c3a-9d6e-2a1b3c4d5e6f', NULL, 'Xray', 'CONSOLE', 'Console', 'CONSOLE',
    'Console', NULL, '2024-01-12 12:00:00', 1704888000000, 0, 255, '*', NULL, 0, 0, 0, 0),
-- 9: inactive permanent ban without a remover: revoked by someone unknown.
(9, '3f0d2a3c-1b5e-4c3a-9d6e-2a1b3c4d5e6f', NULL, 'Old ban', 'CONSOLE', 'Console', NULL,
    NULL, NULL, '2024-01-20 12:00:00', 1704888100000, -1, 255, '*', NULL, 0, 0, 0, 0),
-- 10: an IP ban of a Bedrock name that never joined: a failure.
(10, '#offline#', '.BedrockKid', 'Too young', 'CONSOLE', 'Console', NULL, NULL, NULL,
    '2024-04-06 10:00:00', 1712397600000, -1, 255, '*', NULL, 0, 1, 0, 1),
-- 11: a corrupt UUID: a failure.
(11, 'not-a-uuid', NULL, 'Broken', 'CONSOLE', 'Console', NULL, NULL, NULL,
    '2024-04-07 10:00:00', 1712484000000, -1, 255, '*', NULL, 0, 0, 0, 1),
-- 12: an over-long reason, cut to 1024 characters.
(12, '3f0d2a3c-1b5e-4c3a-9d6e-2a1b3c4d5e6f', NULL, REPEAT('Spam ', 300), 'CONSOLE',
    'Console', NULL, NULL, NULL, '2024-04-08 10:00:00', 1712570400000, 1893456000000, 255,
    'survival', NULL, 0, 0, 0, 1),
-- 13: an IP ban of Steve while his address was unknown: the account part.
(13, '8667ba71-b85a-4004-af54-457a9734eed7', '#offline#', 'Ban evasion', 'CONSOLE',
    'Console', NULL, NULL, NULL, '2024-04-09 10:00:00', 1712656800000, -1, 255, 'creative',
    NULL, 0, 1, 0, 1);

INSERT INTO litebans_mutes (id, uuid, ip, reason, banned_by_uuid, banned_by_name,
    removed_by_uuid, removed_by_name, removed_by_reason, removed_by_date, time, until,
    template, server_scope, server_origin, silent, ipban, ipban_wildcard, active) VALUES
-- 1: active temporary mute.
(1, '8667ba71-b85a-4004-af54-457a9734eed7', '203.0.113.6', 'Caps',
    '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Mod', NULL, NULL, NULL, '2024-04-01 10:00:00',
    1711965600000, 1893456000000, 255, '*', 'lobby', 0, 0, 0, 1),
-- 2: an old LiteBans run out without #expired: no remover, ended after its until.
(2, 'ec561538-f3fd-461d-aff5-086b22154bce', NULL, 'Swearing', 'CONSOLE', 'Console', NULL,
    NULL, NULL, '2024-01-05 12:00:00', 1704189600000, 1704276000000, 255, '*', NULL, 0, 0,
    0, 0);

INSERT INTO litebans_warnings (id, uuid, ip, reason, banned_by_uuid, banned_by_name,
    removed_by_uuid, removed_by_name, removed_by_reason, removed_by_date, time, until,
    template, server_scope, server_origin, silent, ipban, ipban_wildcard, active, warned)
    VALUES
-- 1: active warning the player saw.
(1, 'ec561538-f3fd-461d-aff5-086b22154bce', NULL, 'Be nice',
    '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Mod', NULL, NULL, NULL, '2024-04-01 10:00:00',
    1711965600000, 0, 255, '*', NULL, 0, 0, 0, 1, 1),
-- 2: revoked warning.
(2, 'ec561538-f3fd-461d-aff5-086b22154bce', NULL, 'Mistake',
    '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Mod', '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b',
    'Mod', NULL, '2024-04-02 10:00:00', 1711965700000, 0, 255, '*', NULL, 0, 0, 0, 0, 0);

INSERT INTO litebans_kicks (id, uuid, ip, reason, banned_by_uuid, banned_by_name, time,
    until, template, server_scope, server_origin, silent, ipban, ipban_wildcard, active)
    VALUES
-- 1: kick by the console on one server.
(1, '8667ba71-b85a-4004-af54-457a9734eed7', '203.0.113.6', 'Relog', 'CONSOLE', 'Console',
    1712000000000, 0, 255, 'kitpvp', 'kitpvp', 0, 0, 0, 1),
-- 2: kick by a player.
(2, 'ec561538-f3fd-461d-aff5-086b22154bce', '198.51.100.7', 'AFK',
    '0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b', 'Mod', 1712000100000, 0, 255, '*', NULL, 0, 0,
    0, 1);
