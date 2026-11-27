package net.serlith.sakurabans.core.storage.dbo;

import net.serlith.sakurabans.core.storage.dbo.records.SbActivePunishmentsRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbAllowedAddressesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbAllowedPlayersRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbAuditLogRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbEditorSessionsRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbInstancesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbLockdownsRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbMessageOverridesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbMetaRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayerAddressesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayerNamesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayersRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentEvidenceRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbScopesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbSettingOverridesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbSyncMessagesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplateGroupMembersRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplateGroupsRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplateStepsRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplateUsesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplatesRecord;
import net.serlith.sakurabans.core.storage.dbo.records.SbVictimsRecord;
import org.jooq.ForeignKey;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;
import org.jooq.impl.QOM;

@SuppressWarnings({"unchecked", "UnstableApiUsage"})
public final class Keys {

    public static final UniqueKey<SbMetaRecord> META_PK =
            Internal.createUniqueKey(SakuraTables.META, name("meta_pk"),
                    new TableField[] {SakuraTables.META.K}, true);

    public static final UniqueKey<SbPlayersRecord> PLAYERS_PK =
            Internal.createUniqueKey(SakuraTables.PLAYERS, name("players_pk"),
                    new TableField[] {SakuraTables.PLAYERS.UUID}, true);

    public static final UniqueKey<SbPlayerNamesRecord> PLAYER_NAMES_PK =
            Internal.createUniqueKey(SakuraTables.PLAYER_NAMES, name("player_names_pk"),
                    new TableField[] {
                            SakuraTables.PLAYER_NAMES.UUID,
                            SakuraTables.PLAYER_NAMES.NAME_LOWER
                    }, true);

    public static final UniqueKey<SbPlayerAddressesRecord> PLAYER_ADDRESSES_PK =
            Internal.createUniqueKey(SakuraTables.PLAYER_ADDRESSES, name("player_addresses_pk"),
                    new TableField[] {
                            SakuraTables.PLAYER_ADDRESSES.UUID,
                            SakuraTables.PLAYER_ADDRESSES.ADDRESS
                    }, true);

    public static final UniqueKey<SbScopesRecord> SCOPES_PK =
            Internal.createUniqueKey(SakuraTables.SCOPES, name("scopes_pk"),
                    new TableField[] {SakuraTables.SCOPES.ID}, true);

    public static final UniqueKey<SbScopesRecord> SCOPES_IDENTITY_UQ =
            Internal.createUniqueKey(SakuraTables.SCOPES, name("scopes_identity_uq"),
                    new TableField[] {SakuraTables.SCOPES.KIND, SakuraTables.SCOPES.VALUE}, true);

    public static final UniqueKey<SbVictimsRecord> VICTIMS_PK =
            Internal.createUniqueKey(SakuraTables.VICTIMS, name("victims_pk"),
                    new TableField[] {SakuraTables.VICTIMS.ID}, true);

    public static final UniqueKey<SbVictimsRecord> VICTIMS_IDENTITY_UQ =
            Internal.createUniqueKey(SakuraTables.VICTIMS, name("victims_identity_uq"),
                    new TableField[] {
                            SakuraTables.VICTIMS.KIND,
                            SakuraTables.VICTIMS.UUID,
                            SakuraTables.VICTIMS.ADDRESS_HASH,
                            SakuraTables.VICTIMS.PREFIX_LENGTH
                    }, true);

    public static final UniqueKey<SbTemplatesRecord> TEMPLATES_PK =
            Internal.createUniqueKey(SakuraTables.TEMPLATES, name("templates_pk"),
                    new TableField[] {SakuraTables.TEMPLATES.ID}, true);

    public static final UniqueKey<SbTemplatesRecord> TEMPLATES_VARIANT_NAME_UQ =
            Internal.createUniqueKey(SakuraTables.TEMPLATES, name("templates_variant_name_uq"),
                    new TableField[] {
                            SakuraTables.TEMPLATES.VARIANT_SCOPE_ID,
                            SakuraTables.TEMPLATES.NAME
                    }, true);

    public static final UniqueKey<SbTemplateStepsRecord> TEMPLATE_STEPS_PK =
            Internal.createUniqueKey(SakuraTables.TEMPLATE_STEPS, name("template_steps_pk"),
                    new TableField[] {
                            SakuraTables.TEMPLATE_STEPS.TEMPLATE_ID,
                            SakuraTables.TEMPLATE_STEPS.POSITION
                    }, true);

    public static final UniqueKey<SbTemplateGroupsRecord> TEMPLATE_GROUPS_PK =
            Internal.createUniqueKey(SakuraTables.TEMPLATE_GROUPS, name("template_groups_pk"),
                    new TableField[] {SakuraTables.TEMPLATE_GROUPS.ID}, true);

    public static final UniqueKey<SbTemplateGroupsRecord> TEMPLATE_GROUPS_NAME_UQ =
            Internal.createUniqueKey(SakuraTables.TEMPLATE_GROUPS, name("template_groups_name_uq"),
                    new TableField[] {SakuraTables.TEMPLATE_GROUPS.NAME}, true);

    public static final UniqueKey<SbTemplateGroupMembersRecord> TEMPLATE_GROUP_MEMBERS_PK =
            Internal.createUniqueKey(SakuraTables.TEMPLATE_GROUP_MEMBERS,
                    name("template_group_members_pk"),
                    new TableField[] {
                            SakuraTables.TEMPLATE_GROUP_MEMBERS.GROUP_ID,
                            SakuraTables.TEMPLATE_GROUP_MEMBERS.TEMPLATE_ID
                    }, true);

    public static final UniqueKey<SbPunishmentsRecord> PUNISHMENTS_PK =
            Internal.createUniqueKey(SakuraTables.PUNISHMENTS, name("punishments_pk"),
                    new TableField[] {SakuraTables.PUNISHMENTS.ID}, true);

    public static final UniqueKey<SbPunishmentsRecord> PUNISHMENTS_EXTERNAL_ID_UQ =
            Internal.createUniqueKey(SakuraTables.PUNISHMENTS, name("punishments_external_id_uq"),
                    new TableField[] {SakuraTables.PUNISHMENTS.EXTERNAL_ID}, true);

    public static final UniqueKey<SbActivePunishmentsRecord> ACTIVE_PUNISHMENTS_PK =
            Internal.createUniqueKey(SakuraTables.ACTIVE_PUNISHMENTS, name("active_punishments_pk"),
                    new TableField[] {SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID}, true);

    public static final UniqueKey<SbActivePunishmentsRecord> ACTIVE_PUNISHMENTS_EXCLUSIVE_UQ =
            Internal.createUniqueKey(SakuraTables.ACTIVE_PUNISHMENTS,
                    name("active_punishments_exclusive_uq"),
                    new TableField[] {
                            SakuraTables.ACTIVE_PUNISHMENTS.TYPE,
                            SakuraTables.ACTIVE_PUNISHMENTS.VICTIM_ID,
                            SakuraTables.ACTIVE_PUNISHMENTS.SCOPE_ID,
                            SakuraTables.ACTIVE_PUNISHMENTS.EXCLUSIVITY_SLOT
                    }, true);

    public static final UniqueKey<SbTemplateUsesRecord> TEMPLATE_USES_PK =
            Internal.createUniqueKey(SakuraTables.TEMPLATE_USES, name("template_uses_pk"),
                    new TableField[] {SakuraTables.TEMPLATE_USES.ID}, true);

    public static final UniqueKey<SbTemplateUsesRecord> TEMPLATE_USES_PUNISHMENT_UQ =
            Internal.createUniqueKey(SakuraTables.TEMPLATE_USES,
                    name("template_uses_punishment_uq"),
                    new TableField[] {SakuraTables.TEMPLATE_USES.PUNISHMENT_ID}, true);

    public static final UniqueKey<SbPunishmentEvidenceRecord> PUNISHMENT_EVIDENCE_PK =
            Internal.createUniqueKey(SakuraTables.PUNISHMENT_EVIDENCE,
                    name("punishment_evidence_pk"),
                    new TableField[] {
                            SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID,
                            SakuraTables.PUNISHMENT_EVIDENCE.URI
                    }, true);

    public static final UniqueKey<SbAuditLogRecord> AUDIT_LOG_PK =
            Internal.createUniqueKey(SakuraTables.AUDIT_LOG, name("audit_log_pk"),
                    new TableField[] {SakuraTables.AUDIT_LOG.ID}, true);

    public static final UniqueKey<SbAllowedPlayersRecord> ALLOWED_PLAYERS_PK =
            Internal.createUniqueKey(SakuraTables.ALLOWED_PLAYERS, name("allowed_players_pk"),
                    new TableField[] {SakuraTables.ALLOWED_PLAYERS.UUID}, true);

    public static final UniqueKey<SbAllowedAddressesRecord> ALLOWED_ADDRESSES_PK =
            Internal.createUniqueKey(SakuraTables.ALLOWED_ADDRESSES, name("allowed_addresses_pk"),
                    new TableField[] {SakuraTables.ALLOWED_ADDRESSES.ADDRESS}, true);

    public static final UniqueKey<SbLockdownsRecord> LOCKDOWNS_PK =
            Internal.createUniqueKey(SakuraTables.LOCKDOWNS, name("lockdowns_pk"),
                    new TableField[] {SakuraTables.LOCKDOWNS.SCOPE_ID}, true);

    public static final UniqueKey<SbEditorSessionsRecord> EDITOR_SESSIONS_PK =
            Internal.createUniqueKey(SakuraTables.EDITOR_SESSIONS, name("editor_sessions_pk"),
                    new TableField[] {SakuraTables.EDITOR_SESSIONS.ID}, true);

    public static final UniqueKey<SbInstancesRecord> INSTANCES_PK =
            Internal.createUniqueKey(SakuraTables.INSTANCES, name("instances_pk"),
                    new TableField[] {SakuraTables.INSTANCES.ID}, true);

    public static final UniqueKey<SbSyncMessagesRecord> SYNC_MESSAGES_PK =
            Internal.createUniqueKey(SakuraTables.SYNC_MESSAGES, name("sync_messages_pk"),
                    new TableField[] {SakuraTables.SYNC_MESSAGES.ID}, true);

    public static final UniqueKey<SbSettingOverridesRecord> SETTING_OVERRIDES_PK =
            Internal.createUniqueKey(SakuraTables.SETTING_OVERRIDES, name("setting_overrides_pk"),
                    new TableField[] {SakuraTables.SETTING_OVERRIDES.SCOPE_ID,
                            SakuraTables.SETTING_OVERRIDES.K}, true);

    public static final UniqueKey<SbMessageOverridesRecord> MESSAGE_OVERRIDES_PK =
            Internal.createUniqueKey(SakuraTables.MESSAGE_OVERRIDES, name("message_overrides_pk"),
                    new TableField[] {
                            SakuraTables.MESSAGE_OVERRIDES.SCOPE_ID,
                            SakuraTables.MESSAGE_OVERRIDES.LOCALE,
                            SakuraTables.MESSAGE_OVERRIDES.K
                    }, true);

    public static final ForeignKey<SbPlayerNamesRecord, SbPlayersRecord>
            PLAYER_NAMES_PLAYER_FK = Internal.createForeignKey(
                    SakuraTables.PLAYER_NAMES, name("player_names_player_fk"),
                    new TableField[] {SakuraTables.PLAYER_NAMES.UUID},
                    PLAYERS_PK,
                    new TableField[] {SakuraTables.PLAYERS.UUID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbPlayerAddressesRecord, SbPlayersRecord>
            PLAYER_ADDRESSES_PLAYER_FK = Internal.createForeignKey(
                    SakuraTables.PLAYER_ADDRESSES, name("player_addresses_player_fk"),
                    new TableField[] {SakuraTables.PLAYER_ADDRESSES.UUID},
                    PLAYERS_PK,
                    new TableField[] {SakuraTables.PLAYERS.UUID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbTemplatesRecord, SbScopesRecord>
            TEMPLATES_VARIANT_SCOPE_FK = Internal.createForeignKey(
                    SakuraTables.TEMPLATES, name("templates_variant_scope_fk"),
                    new TableField[] {SakuraTables.TEMPLATES.VARIANT_SCOPE_ID},
                    SCOPES_PK,
                    new TableField[] {SakuraTables.SCOPES.ID},
                    true, QOM.ForeignKeyRule.NO_ACTION, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbTemplatesRecord, SbScopesRecord>
            TEMPLATES_SCOPE_FK = Internal.createForeignKey(
                    SakuraTables.TEMPLATES, name("templates_scope_fk"),
                    new TableField[] {SakuraTables.TEMPLATES.SCOPE_ID},
                    SCOPES_PK,
                    new TableField[] {SakuraTables.SCOPES.ID},
                    true, QOM.ForeignKeyRule.NO_ACTION, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbTemplateStepsRecord, SbTemplatesRecord>
            TEMPLATE_STEPS_TEMPLATE_FK = Internal.createForeignKey(
                    SakuraTables.TEMPLATE_STEPS, name("template_steps_template_fk"),
                    new TableField[] {SakuraTables.TEMPLATE_STEPS.TEMPLATE_ID},
                    TEMPLATES_PK,
                    new TableField[] {SakuraTables.TEMPLATES.ID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbTemplateGroupMembersRecord, SbTemplateGroupsRecord>
            TEMPLATE_GROUP_MEMBERS_GROUP_FK = Internal.createForeignKey(
                    SakuraTables.TEMPLATE_GROUP_MEMBERS, name("template_group_members_group_fk"),
                    new TableField[] {SakuraTables.TEMPLATE_GROUP_MEMBERS.GROUP_ID},
                    TEMPLATE_GROUPS_PK,
                    new TableField[] {SakuraTables.TEMPLATE_GROUPS.ID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbTemplateGroupMembersRecord, SbTemplatesRecord>
            TEMPLATE_GROUP_MEMBERS_TEMPLATE_FK = Internal.createForeignKey(
                    SakuraTables.TEMPLATE_GROUP_MEMBERS, name("template_group_members_template_fk"),
                    new TableField[] {SakuraTables.TEMPLATE_GROUP_MEMBERS.TEMPLATE_ID},
                    TEMPLATES_PK,
                    new TableField[] {SakuraTables.TEMPLATES.ID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbPunishmentsRecord, SbVictimsRecord>
            PUNISHMENTS_VICTIM_FK = Internal.createForeignKey(
                    SakuraTables.PUNISHMENTS, name("punishments_victim_fk"),
                    new TableField[] {SakuraTables.PUNISHMENTS.VICTIM_ID},
                    VICTIMS_PK,
                    new TableField[] {SakuraTables.VICTIMS.ID},
                    true, QOM.ForeignKeyRule.NO_ACTION, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbPunishmentsRecord, SbScopesRecord>
            PUNISHMENTS_SCOPE_FK = Internal.createForeignKey(
                    SakuraTables.PUNISHMENTS, name("punishments_scope_fk"),
                    new TableField[] {SakuraTables.PUNISHMENTS.SCOPE_ID},
                    SCOPES_PK,
                    new TableField[] {SakuraTables.SCOPES.ID},
                    true, QOM.ForeignKeyRule.NO_ACTION, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbActivePunishmentsRecord, SbPunishmentsRecord>
            ACTIVE_PUNISHMENTS_PUNISHMENT_FK = Internal.createForeignKey(
                    SakuraTables.ACTIVE_PUNISHMENTS, name("active_punishments_punishment_fk"),
                    new TableField[] {SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID},
                    PUNISHMENTS_PK,
                    new TableField[] {SakuraTables.PUNISHMENTS.ID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbActivePunishmentsRecord, SbVictimsRecord>
            ACTIVE_PUNISHMENTS_VICTIM_FK = Internal.createForeignKey(
                    SakuraTables.ACTIVE_PUNISHMENTS, name("active_punishments_victim_fk"),
                    new TableField[] {SakuraTables.ACTIVE_PUNISHMENTS.VICTIM_ID},
                    VICTIMS_PK,
                    new TableField[] {SakuraTables.VICTIMS.ID},
                    true, QOM.ForeignKeyRule.NO_ACTION, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbActivePunishmentsRecord, SbScopesRecord>
            ACTIVE_PUNISHMENTS_SCOPE_FK = Internal.createForeignKey(
                    SakuraTables.ACTIVE_PUNISHMENTS, name("active_punishments_scope_fk"),
                    new TableField[] {SakuraTables.ACTIVE_PUNISHMENTS.SCOPE_ID},
                    SCOPES_PK,
                    new TableField[] {SakuraTables.SCOPES.ID},
                    true, QOM.ForeignKeyRule.NO_ACTION, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbPunishmentEvidenceRecord, SbPunishmentsRecord>
            PUNISHMENT_EVIDENCE_PUNISHMENT_FK = Internal.createForeignKey(
                    SakuraTables.PUNISHMENT_EVIDENCE, name("punishment_evidence_punishment_fk"),
                    new TableField[] {SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID},
                    PUNISHMENTS_PK,
                    new TableField[] {SakuraTables.PUNISHMENTS.ID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbPunishmentsRecord, SbScopesRecord>
            PUNISHMENTS_TEMPLATE_VARIANT_FK = Internal.createForeignKey(
                    SakuraTables.PUNISHMENTS, name("punishments_template_variant_fk"),
                    new TableField[] {SakuraTables.PUNISHMENTS.TEMPLATE_VARIANT_SCOPE_ID},
                    SCOPES_PK,
                    new TableField[] {SakuraTables.SCOPES.ID},
                    true, QOM.ForeignKeyRule.NO_ACTION, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbTemplateUsesRecord, SbScopesRecord>
            TEMPLATE_USES_VARIANT_FK = Internal.createForeignKey(
                    SakuraTables.TEMPLATE_USES, name("template_uses_variant_fk"),
                    new TableField[] {SakuraTables.TEMPLATE_USES.TEMPLATE_VARIANT_SCOPE_ID},
                    SCOPES_PK,
                    new TableField[] {SakuraTables.SCOPES.ID},
                    true, QOM.ForeignKeyRule.NO_ACTION, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbTemplateUsesRecord, SbVictimsRecord>
            TEMPLATE_USES_VICTIM_FK = Internal.createForeignKey(
                    SakuraTables.TEMPLATE_USES, name("template_uses_victim_fk"),
                    new TableField[] {SakuraTables.TEMPLATE_USES.VICTIM_ID},
                    VICTIMS_PK,
                    new TableField[] {SakuraTables.VICTIMS.ID},
                    true, QOM.ForeignKeyRule.NO_ACTION, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbTemplateUsesRecord, SbPunishmentsRecord>
            TEMPLATE_USES_PUNISHMENT_FK = Internal.createForeignKey(
                    SakuraTables.TEMPLATE_USES, name("template_uses_punishment_fk"),
                    new TableField[] {SakuraTables.TEMPLATE_USES.PUNISHMENT_ID},
                    PUNISHMENTS_PK,
                    new TableField[] {SakuraTables.PUNISHMENTS.ID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbSettingOverridesRecord, SbScopesRecord>
            SETTING_OVERRIDES_SCOPE_FK = Internal.createForeignKey(
                    SakuraTables.SETTING_OVERRIDES, name("setting_overrides_scope_fk"),
                    new TableField[] {SakuraTables.SETTING_OVERRIDES.SCOPE_ID},
                    SCOPES_PK,
                    new TableField[] {SakuraTables.SCOPES.ID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbMessageOverridesRecord, SbScopesRecord>
            MESSAGE_OVERRIDES_SCOPE_FK = Internal.createForeignKey(
                    SakuraTables.MESSAGE_OVERRIDES, name("message_overrides_scope_fk"),
                    new TableField[] {SakuraTables.MESSAGE_OVERRIDES.SCOPE_ID},
                    SCOPES_PK,
                    new TableField[] {SakuraTables.SCOPES.ID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    public static final ForeignKey<SbLockdownsRecord, SbScopesRecord>
            LOCKDOWNS_SCOPE_FK = Internal.createForeignKey(
                    SakuraTables.LOCKDOWNS, name("lockdowns_scope_fk"),
                    new TableField[] {SakuraTables.LOCKDOWNS.SCOPE_ID},
                    SCOPES_PK,
                    new TableField[] {SakuraTables.SCOPES.ID},
                    true, QOM.ForeignKeyRule.CASCADE, QOM.ForeignKeyRule.NO_ACTION);

    private Keys() {
    }

    private static org.jooq.Name name(final String suffix) {
        return DSL.name(SakuraTables.prefix() + suffix);
    }
}
