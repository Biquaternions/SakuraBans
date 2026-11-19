package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentEvidenceRecord;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbPunishmentEvidence extends SbTable<SbPunishmentEvidenceRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbPunishmentEvidenceRecord, Long> PUNISHMENT_ID = createField(
            DSL.name("punishment_id"), SQLDataType.BIGINT.nullable(false), this, "");

    public final TableField<SbPunishmentEvidenceRecord, String> URI = createField(
            DSL.name("uri"), SQLDataType.VARCHAR(512).nullable(false), this, "");

    public final TableField<SbPunishmentEvidenceRecord, OperatorKind> ADDED_BY_KIND = createField(
            DSL.name("added_by_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbPunishmentEvidenceRecord, UUID> ADDED_BY_UUID = createField(
            DSL.name("added_by_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbPunishmentEvidenceRecord, String> ADDED_BY_NAME = createField(
            DSL.name("added_by_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbPunishmentEvidenceRecord, String> ADDED_BY_SOURCE = createField(
            DSL.name("added_by_source"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbPunishmentEvidenceRecord, Instant> ADDED_AT = createField(
            DSL.name("added_at"), SbTypes.INSTANT.nullable(false), this, "");

    public SbPunishmentEvidence(final String prefix) {
        this(prefix, DSL.name(prefix + "punishment_evidence"), null);
    }

    private SbPunishmentEvidence(final String prefix, final Name alias,
            final @Nullable Table<SbPunishmentEvidenceRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbPunishmentEvidenceRecord> getRecordType() {
        return SbPunishmentEvidenceRecord.class;
    }

    @Override
    public UniqueKey<SbPunishmentEvidenceRecord> getPrimaryKey() {
        return Keys.PUNISHMENT_EVIDENCE_PK;
    }

    @Override
    public List<ForeignKey<SbPunishmentEvidenceRecord, ?>> getReferences() {
        return List.of(
                Keys.PUNISHMENT_EVIDENCE_PUNISHMENT_FK);
    }

    @Override
    public SbPunishmentEvidence as(final String alias) {
        return new SbPunishmentEvidence(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbPunishmentEvidence as(final Name alias) {
        return new SbPunishmentEvidence(this.prefix(), alias, this);
    }

    @Override
    public SbPunishmentEvidence rename(final String name) {
        return new SbPunishmentEvidence(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbPunishmentEvidence rename(final Name name) {
        return new SbPunishmentEvidence(this.prefix(), name, null);
    }
}
