-- AdvancedBan's HSQLDB schema: mixed-case names, unquoted (HSQLDB folds them to upper case).
-- Times are epoch milliseconds, end -1 is permanent, uuid holds an IP for IP punishments and the
-- name on offline-mode servers. Every active punishment is also in PunishmentHistory, under
-- another id.
CREATE TABLE Punishments (
  id INTEGER IDENTITY PRIMARY KEY,
  name VARCHAR(16),
  uuid VARCHAR(35),
  reason VARCHAR(100),
  operator VARCHAR(16),
  punishmentType VARCHAR(16),
  start BIGINT,
  end BIGINT,
  calculation VARCHAR(50)
);
CREATE TABLE PunishmentHistory (
  id INTEGER IDENTITY PRIMARY KEY,
  name VARCHAR(16),
  uuid VARCHAR(35),
  reason VARCHAR(100),
  operator VARCHAR(16),
  punishmentType VARCHAR(16),
  start BIGINT,
  end BIGINT,
  calculation VARCHAR(50)
);
INSERT INTO Punishments VALUES (1, 'Steve', '8667ba71b85a4004af54457a9734eed7', '&cGriefing the &lspawn', 'CONSOLE', 'BAN', 1790856000000, -1, NULL);
INSERT INTO Punishments VALUES (2, 'Alex', 'ec561538f3fd461daff5086b22154bce', 'Spamming', 'Mod', 'TEMP_BAN', 1791192600000, 4070908800000, NULL);
INSERT INTO Punishments VALUES (3, 'OfflineGuy', 'OfflineGuy', 'Offline mode ban', 'Mod', 'BAN', 1791657900000, -1, NULL);
INSERT INTO Punishments VALUES (4, 'Alt', '203.0.113.50', 'Ban evasion', 'CONSOLE', 'IP_BAN', 1791792000000, -1, NULL);
INSERT INTO Punishments VALUES (5, 'Alt', '2001:db8::1', 'Temporary address ban', 'Mod', 'TEMP_IP_BAN', 1792095300000, 4070908800000, NULL);
INSERT INTO Punishments VALUES (6, 'Bob', '0f6b8d6a1d342b4e9c1e5b7a1e1d2c3b', 'Insulting players', 'Mod', 'MUTE', 1793527200000, -1, NULL);
INSERT INTO Punishments VALUES (7, 'Steve', '8667ba71b85a4004af54457a9734eed7', 'Third warning', 'Mod', 'WARNING', 1793527201000, -1, NULL);
INSERT INTO Punishments VALUES (8, 'Bob', '0f6b8d6a1d342b4e9c1e5b7a1e1d2c3b', 'Short mute', 'CONSOLE', 'TEMP_MUTE', 1788220800000, 1788998400000, NULL);
INSERT INTO Punishments VALUES (9, 'Zed', NULL, 'No uuid at all', 'Mod', 'BAN', 1790856000000, -1, NULL);
INSERT INTO Punishments VALUES (10, 'Ecotastic', '00140fe8de0841e9ab79e53b9f3f0fbd', 'noteworthy', 'Mod', 'NOTE', 1790856000000, 1788998400000, NULL);
INSERT INTO PunishmentHistory VALUES (21, 'Steve', '8667ba71b85a4004af54457a9734eed7', '&cGriefing the &lspawn', 'CONSOLE', 'BAN', 1790856000000, -1, NULL);
INSERT INTO PunishmentHistory VALUES (22, 'Alex', 'ec561538f3fd461daff5086b22154bce', 'Spamming', 'Mod', 'TEMP_BAN', 1791192600000, 4070908800000, NULL);
INSERT INTO PunishmentHistory VALUES (23, 'OfflineGuy', 'OfflineGuy', 'Offline mode ban', 'Mod', 'BAN', 1791657900000, -1, NULL);
INSERT INTO PunishmentHistory VALUES (24, 'Alt', '203.0.113.50', 'Ban evasion', 'CONSOLE', 'IP_BAN', 1791792000000, -1, NULL);
INSERT INTO PunishmentHistory VALUES (25, 'Alt', '2001:db8::1', 'Temporary address ban', 'Mod', 'TEMP_IP_BAN', 1792095300000, 4070908800000, NULL);
INSERT INTO PunishmentHistory VALUES (26, 'Bob', '0f6b8d6a1d342b4e9c1e5b7a1e1d2c3b', 'Insulting players', 'Mod', 'MUTE', 1793527200000, -1, NULL);
INSERT INTO PunishmentHistory VALUES (27, 'Steve', '8667ba71b85a4004af54457a9734eed7', 'Third warning', 'Mod', 'WARNING', 1793527201000, -1, NULL);
INSERT INTO PunishmentHistory VALUES (28, 'Bob', '0f6b8d6a1d342b4e9c1e5b7a1e1d2c3b', 'Short mute', 'CONSOLE', 'TEMP_MUTE', 1788220800000, 1788998400000, NULL);
INSERT INTO PunishmentHistory VALUES (29, 'Zed', NULL, 'No uuid at all', 'Mod', 'BAN', 1790856000000, -1, NULL);
INSERT INTO PunishmentHistory VALUES (30, 'Ecotastic', '00140fe8de0841e9ab79e53b9f3f0fbd', 'noteworthy', 'Mod', 'NOTE', 1790856000000, 1788998400000, NULL);
INSERT INTO PunishmentHistory VALUES (31, 'Cara', '394aef836e3f482b9988933cabe3b1cd', 'Removed perm ban', 'Mod', 'BAN', 1788307200000, -1, NULL);
INSERT INTO PunishmentHistory VALUES (32, 'Dan', '949ff2499a0140329ecfc1b610fd7990', 'Expired in history', 'CONSOLE', 'TEMP_BAN', 1785542400000, 1790812800000, NULL);
INSERT INTO PunishmentHistory VALUES (33, 'Eve', '60a3adc0878a449ba7839a2f24ef5906', 'Removed temp ban', 'Mod', 'TEMP_BAN', 1792281600000, 4070908800000, NULL);
INSERT INTO PunishmentHistory VALUES (34, 'Fay', '00140fe8de0841e9ab79e53b9f3f0fbd', 'Kicked for AFK', 'Mod', 'KICK', 1792368000000, 1792368000000, NULL);
INSERT INTO PunishmentHistory VALUES (35, 'Alt', '198.51.100.9', 'Removed address ban', 'Mod', 'IP_BAN', 1792454400000, -1, NULL);
INSERT INTO PunishmentHistory VALUES (36, 'Gus', '60a3adc0878a449ba7839a2f24ef5906', 'Newer AdvancedBan type', 'Mod', 'BANISH', 1792454400000, -1, NULL);
INSERT INTO PunishmentHistory VALUES (37, 'Hal', '949ff2499a0140329ecfc1b610fd7990', 'Missing end', 'Mod', 'BAN', 1792454400000, NULL, NULL);
INSERT INTO PunishmentHistory VALUES (38, 'Ecotastic', '00140fe8de0841e9ab79e53b9f3f0fbd', 'noteworthy', 'Mod', 'NOTE', 1790856000000, 1788998400000, NULL);
