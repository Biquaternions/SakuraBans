package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import org.jooq.Constraint;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbPunishments extends SbTable<SbPunishmentsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbPunishmentsRecord, Long> ID = createField(
            DSL.name("id"), SQLDataType.BIGINT.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbPunishmentsRecord, PunishmentType> TYPE = createField(
            DSL.name("type"), SbTypes.PUNISHMENT_TYPE.nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, String> KIND = createField(
            DSL.name("kind"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, Boolean> SHADOW = createField(
            DSL.name("shadow"), SbTypes.FLAG.nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, AddressStrictness> STRICTNESS = createField(
            DSL.name("strictness"), SbTypes.STRICTNESS.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, Long> VICTIM_ID = createField(
            DSL.name("victim_id"), SQLDataType.BIGINT.nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, Integer> SCOPE_ID = createField(
            DSL.name("scope_id"), SQLDataType.INTEGER.nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, String> REASON = createField(
            DSL.name("reason"), SQLDataType.VARCHAR(1024).nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, OperatorKind> OPERATOR_KIND = createField(
            DSL.name("operator_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, UUID> OPERATOR_UUID = createField(
            DSL.name("operator_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, String> OPERATOR_NAME = createField(
            DSL.name("operator_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, String> SOURCE = createField(
            DSL.name("source"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, Instant> CREATED_AT = createField(
            DSL.name("created_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, Instant> EXPIRES_AT = createField(
            DSL.name("expires_at"), SbTypes.INSTANT.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, String> ORIGIN_SERVER = createField(
            DSL.name("origin_server"), SQLDataType.VARCHAR(64).nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, Visibility> VISIBILITY = createField(
            DSL.name("visibility"), SbTypes.VISIBILITY.nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, String> TEMPLATE = createField(
            DSL.name("template"), SQLDataType.VARCHAR(64).nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, Short> TEMPLATE_STEP = createField(
            DSL.name("template_step"), SQLDataType.SMALLINT.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, Instant> ENDED_AT = createField(
            DSL.name("ended_at"), SbTypes.INSTANT.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, EndKind> END_KIND = createField(
            DSL.name("end_kind"), SbTypes.END_KIND.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, OperatorKind> REVOKER_KIND = createField(
            DSL.name("revoker_kind"), SbTypes.OPERATOR_KIND.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, UUID> REVOKER_UUID = createField(
            DSL.name("revoker_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, String> REVOKER_NAME = createField(
            DSL.name("revoker_name"), SQLDataType.VARCHAR(64).nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, String> REVOKER_SOURCE = createField(
            DSL.name("revoker_source"), SQLDataType.VARCHAR(64).nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, String> REVOKE_REASON = createField(
            DSL.name("revoke_reason"), SQLDataType.VARCHAR(1024).nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, Instant> VOIDED_AT = createField(
            DSL.name("voided_at"), SbTypes.INSTANT.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, OperatorKind> VOIDER_KIND = createField(
            DSL.name("voider_kind"), SbTypes.OPERATOR_KIND.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, UUID> VOIDER_UUID = createField(
            DSL.name("voider_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, String> VOIDER_NAME = createField(
            DSL.name("voider_name"), SQLDataType.VARCHAR(64).nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, String> VOIDER_SOURCE = createField(
            DSL.name("voider_source"), SQLDataType.VARCHAR(64).nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, String> VOID_REASON = createField(
            DSL.name("void_reason"), SQLDataType.VARCHAR(1024).nullable(true), this, "");

    public final TableField<SbPunishmentsRecord, Long> VERSION = createField(
            DSL.name("version"), SQLDataType.BIGINT.nullable(false), this, "");

    public final TableField<SbPunishmentsRecord, String> EXTERNAL_ID = createField(
            DSL.name("external_id"), SQLDataType.VARCHAR(128).nullable(true), this, "");

    public SbPunishments(final String prefix) {
        this(prefix, DSL.name(prefix + "punishments"), null);
    }

    private SbPunishments(final String prefix, final Name alias,
            final @Nullable Table<SbPunishmentsRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbPunishmentsRecord> getRecordType() {
        return SbPunishmentsRecord.class;
    }

    @Override
    public UniqueKey<SbPunishmentsRecord> getPrimaryKey() {
        return Keys.PUNISHMENTS_PK;
    }

    @Override
    public List<UniqueKey<SbPunishmentsRecord>> getUniqueKeys() {
        return List.of(Keys.PUNISHMENTS_EXTERNAL_ID_UQ);
    }

    @Override
    public List<ForeignKey<SbPunishmentsRecord, ?>> getReferences() {
        return List.of(
                Keys.PUNISHMENTS_VICTIM_FK,
                Keys.PUNISHMENTS_SCOPE_FK);
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.PUNISHMENTS_VICTIM_IX,
                Indexes.PUNISHMENTS_OPERATOR_IX,
                Indexes.PUNISHMENTS_CREATED_IX,
                Indexes.PUNISHMENTS_TEMPLATE_IX,
                Indexes.PUNISHMENTS_REVOKER_IX);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("expiry_ck",
                        this.EXPIRES_AT.isNull().or(this.EXPIRES_AT.gt(this.CREATED_AT))),
                this.check("kick_ck",
                        this.TYPE.ne(PunishmentType.KICK).or(this.EXPIRES_AT.isNull())),
                this.check("operator_ck",
                        this.OPERATOR_KIND.ne(OperatorKind.PLAYER)
                                .or(this.OPERATOR_UUID.isNotNull())),
                this.check("end_ck",
                        this.ENDED_AT.isNull().and(this.END_KIND.isNull())
                                .and(this.REVOKER_KIND.isNull())
                                .or(this.ENDED_AT.isNotNull().and(this.END_KIND.isNotNull()))),
                this.check("void_ck",
                        this.VOIDED_AT.isNull().and(this.VOIDER_KIND.isNull())
                                .or(this.VOIDED_AT.isNotNull().and(this.VOIDER_KIND.isNotNull()))),
                this.check("version_ck",
                        this.VERSION.ge(1L)));
    }

    @Override
    public SbPunishments as(final String alias) {
        return new SbPunishments(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbPunishments as(final Name alias) {
        return new SbPunishments(this.prefix(), alias, this);
    }

    @Override
    public SbPunishments rename(final String name) {
        return new SbPunishments(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbPunishments rename(final Name name) {
        return new SbPunishments(this.prefix(), name, null);
    }
}
