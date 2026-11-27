package net.serlith.sakurabans.core.storage.dbo;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;

public final class Indexes {

    public static final Index PLAYERS_LAST_NAME_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "players_last_name_ix"),
                    SakuraTables.PLAYERS,
                    new OrderField<?>[] {SakuraTables.PLAYERS.LAST_NAME_LOWER}, false);

    public static final Index PLAYER_NAMES_NAME_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "player_names_name_ix"),
                    SakuraTables.PLAYER_NAMES,
                    new OrderField<?>[] {
                            SakuraTables.PLAYER_NAMES.NAME_LOWER,
                            SakuraTables.PLAYER_NAMES.LAST_SEEN
                    }, false);

    public static final Index PLAYER_ADDRESSES_ADDRESS_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "player_addresses_address_ix"),
                    SakuraTables.PLAYER_ADDRESSES,
                    new OrderField<?>[] {
                            SakuraTables.PLAYER_ADDRESSES.ADDRESS,
                            SakuraTables.PLAYER_ADDRESSES.LAST_SEEN
                    }, false);

    public static final Index VICTIMS_UUID_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "victims_uuid_ix"),
                    SakuraTables.VICTIMS,
                    new OrderField<?>[] {SakuraTables.VICTIMS.UUID}, false);

    public static final Index VICTIMS_ADDRESS_HASH_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "victims_address_hash_ix"),
                    SakuraTables.VICTIMS,
                    new OrderField<?>[] {SakuraTables.VICTIMS.ADDRESS_HASH}, false);

    public static final Index PUNISHMENTS_VICTIM_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "punishments_victim_ix"),
                    SakuraTables.PUNISHMENTS,
                    new OrderField<?>[] {
                            SakuraTables.PUNISHMENTS.VICTIM_ID,
                            SakuraTables.PUNISHMENTS.TYPE,
                            SakuraTables.PUNISHMENTS.CREATED_AT
                    }, false);

    public static final Index PUNISHMENTS_OPERATOR_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "punishments_operator_ix"),
                    SakuraTables.PUNISHMENTS,
                    new OrderField<?>[] {
                            SakuraTables.PUNISHMENTS.OPERATOR_UUID,
                            SakuraTables.PUNISHMENTS.CREATED_AT
                    }, false);

    public static final Index PUNISHMENTS_CREATED_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "punishments_created_ix"),
                    SakuraTables.PUNISHMENTS,
                    new OrderField<?>[] {SakuraTables.PUNISHMENTS.CREATED_AT}, false);

    public static final Index PUNISHMENTS_TYPE_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "punishments_type_ix"),
                    SakuraTables.PUNISHMENTS,
                    new OrderField<?>[] {
                            SakuraTables.PUNISHMENTS.TYPE,
                            SakuraTables.PUNISHMENTS.VOIDED_AT
                    }, false);

    public static final Index TEMPLATE_USES_TEMPLATE_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "template_uses_template_ix"),
                    SakuraTables.TEMPLATE_USES,
                    new OrderField<?>[] {
                            SakuraTables.TEMPLATE_USES.TEMPLATE,
                            SakuraTables.TEMPLATE_USES.VICTIM_ID,
                            SakuraTables.TEMPLATE_USES.CREATED_AT
                    }, false);

    public static final Index PUNISHMENTS_REVOKER_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "punishments_revoker_ix"),
                    SakuraTables.PUNISHMENTS,
                    new OrderField<?>[] {
                            SakuraTables.PUNISHMENTS.REVOKER_UUID,
                            SakuraTables.PUNISHMENTS.ENDED_AT
                    }, false);

    public static final Index ACTIVE_PUNISHMENTS_VICTIM_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "active_punishments_victim_ix"),
                    SakuraTables.ACTIVE_PUNISHMENTS,
                    new OrderField<?>[] {SakuraTables.ACTIVE_PUNISHMENTS.VICTIM_ID}, false);

    public static final Index ACTIVE_PUNISHMENTS_EXPIRY_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "active_punishments_expiry_ix"),
                    SakuraTables.ACTIVE_PUNISHMENTS,
                    new OrderField<?>[] {SakuraTables.ACTIVE_PUNISHMENTS.EXPIRES_AT}, false);

    public static final Index AUDIT_LOG_SUBJECT_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "audit_log_subject_ix"),
                    SakuraTables.AUDIT_LOG,
                    new OrderField<?>[] {
                            SakuraTables.AUDIT_LOG.SUBJECT_TYPE,
                            SakuraTables.AUDIT_LOG.SUBJECT_ID
                    }, false);

    public static final Index AUDIT_LOG_AT_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "audit_log_at_ix"),
                    SakuraTables.AUDIT_LOG,
                    new OrderField<?>[] {SakuraTables.AUDIT_LOG.AT}, false);

    public static final Index AUDIT_LOG_ACTOR_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "audit_log_actor_ix"),
                    SakuraTables.AUDIT_LOG,
                    new OrderField<?>[] {
                            SakuraTables.AUDIT_LOG.ACTOR_UUID,
                            SakuraTables.AUDIT_LOG.AT
                    }, false);

    public static final Index AUDIT_LOG_ACTION_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "audit_log_action_ix"),
                    SakuraTables.AUDIT_LOG,
                    new OrderField<?>[] {
                            SakuraTables.AUDIT_LOG.ACTION,
                            SakuraTables.AUDIT_LOG.AT
                    }, false);

    public static final Index AUDIT_LOG_KIND_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "audit_log_kind_ix"),
                    SakuraTables.AUDIT_LOG,
                    new OrderField<?>[] {
                            SakuraTables.AUDIT_LOG.SUBJECT_TYPE,
                            SakuraTables.AUDIT_LOG.AT
                    }, false);

    public static final Index SYNC_MESSAGES_CREATED_IX =
            Internal.createIndex(DSL.name(SakuraTables.prefix() + "sync_messages_created_ix"),
                    SakuraTables.SYNC_MESSAGES,
                    new OrderField<?>[] {SakuraTables.SYNC_MESSAGES.CREATED_AT}, false);

    private Indexes() {
    }
}
