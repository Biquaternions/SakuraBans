package net.serlith.sakurabans.core.storage.dbo;

import java.util.List;
import java.util.Objects;
import net.serlith.sakurabans.core.storage.dbo.tables.SbActivePunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAllowedAddresses;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAllowedPlayers;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAppeals;
import net.serlith.sakurabans.core.storage.dbo.tables.SbAuditLog;
import net.serlith.sakurabans.core.storage.dbo.tables.SbBanWaveEntries;
import net.serlith.sakurabans.core.storage.dbo.tables.SbBanWaves;
import net.serlith.sakurabans.core.storage.dbo.tables.SbInstances;
import net.serlith.sakurabans.core.storage.dbo.tables.SbKinds;
import net.serlith.sakurabans.core.storage.dbo.tables.SbLockdowns;
import net.serlith.sakurabans.core.storage.dbo.tables.SbMessageOverrides;
import net.serlith.sakurabans.core.storage.dbo.tables.SbMeta;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPlayerAddresses;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPlayerNames;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPlayers;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishmentEvidence;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPunishments;
import net.serlith.sakurabans.core.storage.dbo.tables.SbScopes;
import net.serlith.sakurabans.core.storage.dbo.tables.SbSettingOverrides;
import net.serlith.sakurabans.core.storage.dbo.tables.SbSyncMessages;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateGroupMembers;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateGroups;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplateSteps;
import net.serlith.sakurabans.core.storage.dbo.tables.SbTemplates;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("NotNullFieldNotInitialized")
public final class SakuraTables {

    public static final int SCHEMA_VERSION = 1;

    public static SbMeta META;
    public static SbPlayers PLAYERS;
    public static SbPlayerNames PLAYER_NAMES;
    public static SbPlayerAddresses PLAYER_ADDRESSES;
    public static SbScopes SCOPES;
    public static SbVictims VICTIMS;
    public static SbKinds KINDS;
    public static SbTemplates TEMPLATES;
    public static SbTemplateSteps TEMPLATE_STEPS;
    public static SbTemplateGroups TEMPLATE_GROUPS;
    public static SbTemplateGroupMembers TEMPLATE_GROUP_MEMBERS;
    public static SbPunishments PUNISHMENTS;
    public static SbActivePunishments ACTIVE_PUNISHMENTS;
    public static SbPunishmentEvidence PUNISHMENT_EVIDENCE;
    public static SbAuditLog AUDIT_LOG;
    public static SbAllowedPlayers ALLOWED_PLAYERS;
    public static SbAllowedAddresses ALLOWED_ADDRESSES;
    public static SbInstances INSTANCES;
    public static SbLockdowns LOCKDOWNS;
    public static SbSyncMessages SYNC_MESSAGES;
    public static SbBanWaves BAN_WAVES;
    public static SbBanWaveEntries BAN_WAVE_ENTRIES;
    public static SbAppeals APPEALS;
    public static SbSettingOverrides SETTING_OVERRIDES;
    public static SbMessageOverrides MESSAGE_OVERRIDES;

    private static @Nullable String prefix;
    private static @Nullable SakuraDatabase database;

    private SakuraTables() {
    }

    /**
     * Builds every table. Calling it again with the same values does nothing; different values
     * throw, because {@link Keys} and {@link Indexes} already captured the old tables.
     */
    public static synchronized void initialize(final String tablePrefix,
            final @Nullable String schema) {
        final String schemaName = schema == null ? "" : schema;
        if (prefix != null) {
            if (!prefix.equals(tablePrefix) || !Objects.requireNonNull(database).getName()
                    .equals(schemaName)) {
                throw new IllegalStateException("the table prefix or schema changed from '"
                        + prefix + "' (schema '" + database.getName() + "') to '" + tablePrefix
                        + "' (schema '" + schemaName + "'); restart to apply it");
            }
            return;
        }
        META = new SbMeta(tablePrefix);
        PLAYERS = new SbPlayers(tablePrefix);
        PLAYER_NAMES = new SbPlayerNames(tablePrefix);
        PLAYER_ADDRESSES = new SbPlayerAddresses(tablePrefix);
        SCOPES = new SbScopes(tablePrefix);
        VICTIMS = new SbVictims(tablePrefix);
        KINDS = new SbKinds(tablePrefix);
        TEMPLATES = new SbTemplates(tablePrefix);
        TEMPLATE_STEPS = new SbTemplateSteps(tablePrefix);
        TEMPLATE_GROUPS = new SbTemplateGroups(tablePrefix);
        TEMPLATE_GROUP_MEMBERS = new SbTemplateGroupMembers(tablePrefix);
        PUNISHMENTS = new SbPunishments(tablePrefix);
        ACTIVE_PUNISHMENTS = new SbActivePunishments(tablePrefix);
        PUNISHMENT_EVIDENCE = new SbPunishmentEvidence(tablePrefix);
        AUDIT_LOG = new SbAuditLog(tablePrefix);
        ALLOWED_PLAYERS = new SbAllowedPlayers(tablePrefix);
        ALLOWED_ADDRESSES = new SbAllowedAddresses(tablePrefix);
        INSTANCES = new SbInstances(tablePrefix);
        LOCKDOWNS = new SbLockdowns(tablePrefix);
        SYNC_MESSAGES = new SbSyncMessages(tablePrefix);
        BAN_WAVES = new SbBanWaves(tablePrefix);
        BAN_WAVE_ENTRIES = new SbBanWaveEntries(tablePrefix);
        APPEALS = new SbAppeals(tablePrefix);
        SETTING_OVERRIDES = new SbSettingOverrides(tablePrefix);
        MESSAGE_OVERRIDES = new SbMessageOverrides(tablePrefix);
        SakuraTables.database = new SakuraDatabase(schemaName, new SakuraCatalog(), all());
        SakuraTables.prefix = tablePrefix;
    }

    public static String prefix() {
        final String current = prefix;
        if (current == null) {
            throw new IllegalStateException("SakuraTables.initialize wasn't called");
        }
        return current;
    }

    public static SakuraDatabase database() {
        final SakuraDatabase current = database;
        if (current == null) {
            throw new IllegalStateException("SakuraTables.initialize wasn't called");
        }
        return current;
    }

    /** Every table in creation order: a table comes after the tables it references. */
    public static List<SbTable<?>> all() {
        return List.of(
                META,
                PLAYERS,
                PLAYER_NAMES,
                PLAYER_ADDRESSES,
                SCOPES,
                VICTIMS,
                KINDS,
                TEMPLATES,
                TEMPLATE_STEPS,
                TEMPLATE_GROUPS,
                TEMPLATE_GROUP_MEMBERS,
                PUNISHMENTS,
                ACTIVE_PUNISHMENTS,
                PUNISHMENT_EVIDENCE,
                AUDIT_LOG,
                ALLOWED_PLAYERS,
                ALLOWED_ADDRESSES,
                INSTANCES,
                LOCKDOWNS,
                SYNC_MESSAGES,
                BAN_WAVES,
                BAN_WAVE_ENTRIES,
                APPEALS,
                SETTING_OVERRIDES,
                MESSAGE_OVERRIDES);
    }
}
