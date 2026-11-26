-- A made-up plugin's tables, for SourceDatabaseTest: one ban per row, times in epoch seconds.
CREATE TABLE ex_bans (
    id INTEGER PRIMARY KEY,
    uuid VARCHAR(36),
    ip VARCHAR(45),
    reason VARCHAR(255),
    banned_by VARCHAR(16),
    created BIGINT NOT NULL,
    ends BIGINT NOT NULL
);
INSERT INTO ex_bans VALUES (1, '8667ba71-b85a-4004-af54-457a9734eed7', NULL, '&cCheating', 'CONSOLE',
    1700000000, 0);
INSERT INTO ex_bans VALUES (2, NULL, '203.0.113.7', 'Alt', 'Mod', 1700000000, 4102444800);
INSERT INTO ex_bans VALUES (3, NULL, NULL, 'Nobody', 'Mod', 1700000000, 0);
