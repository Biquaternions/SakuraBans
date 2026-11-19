package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbActivePunishmentsRecord;
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

/**
 * Only active bans, mutes and warns; what enforcement reads (DESIGN 7.3).
 *
 * <p>A warn's {@code exclusivity_slot} is its {@code punishment_id}, written by the
 * repository: MySQL forbids a CHECK on a column with a foreign key action.
 */
public final class SbActivePunishments extends SbTable<SbActivePunishmentsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbActivePunishmentsRecord, Long> PUNISHMENT_ID = createField(
            DSL.name("punishment_id"), SQLDataType.BIGINT.nullable(false), this, "");

    public final TableField<SbActivePunishmentsRecord, PunishmentType> TYPE = createField(
            DSL.name("type"), SbTypes.PUNISHMENT_TYPE.nullable(false), this, "");

    public final TableField<SbActivePunishmentsRecord, Long> VICTIM_ID = createField(
            DSL.name("victim_id"), SQLDataType.BIGINT.nullable(false), this, "");

    public final TableField<SbActivePunishmentsRecord, Integer> SCOPE_ID = createField(
            DSL.name("scope_id"), SQLDataType.INTEGER.nullable(false), this, "");

    public final TableField<SbActivePunishmentsRecord, Instant> EXPIRES_AT = createField(
            DSL.name("expires_at"), SbTypes.INSTANT.nullable(true), this, "");

    public final TableField<SbActivePunishmentsRecord, Long> EXCLUSIVITY_SLOT = createField(
            DSL.name("exclusivity_slot"), SQLDataType.BIGINT.nullable(false), this, "");

    public SbActivePunishments(final String prefix) {
        this(prefix, DSL.name(prefix + "active_punishments"), null);
    }

    private SbActivePunishments(final String prefix, final Name alias,
            final @Nullable Table<SbActivePunishmentsRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbActivePunishmentsRecord> getRecordType() {
        return SbActivePunishmentsRecord.class;
    }

    @Override
    public UniqueKey<SbActivePunishmentsRecord> getPrimaryKey() {
        return Keys.ACTIVE_PUNISHMENTS_PK;
    }

    @Override
    public List<UniqueKey<SbActivePunishmentsRecord>> getUniqueKeys() {
        return List.of(Keys.ACTIVE_PUNISHMENTS_EXCLUSIVE_UQ);
    }

    @Override
    public List<ForeignKey<SbActivePunishmentsRecord, ?>> getReferences() {
        return List.of(
                Keys.ACTIVE_PUNISHMENTS_PUNISHMENT_FK,
                Keys.ACTIVE_PUNISHMENTS_VICTIM_FK,
                Keys.ACTIVE_PUNISHMENTS_SCOPE_FK);
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.ACTIVE_PUNISHMENTS_VICTIM_IX,
                Indexes.ACTIVE_PUNISHMENTS_EXPIRY_IX);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("slot_ck",
                        this.TYPE.eq(PunishmentType.WARN)
                                .and(this.EXCLUSIVITY_SLOT.gt(0L))
                                .or(this.TYPE.in(PunishmentType.BAN, PunishmentType.MUTE)
                                .and(this.EXCLUSIVITY_SLOT.eq(0L)))));
    }

    @Override
    public SbActivePunishments as(final String alias) {
        return new SbActivePunishments(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbActivePunishments as(final Name alias) {
        return new SbActivePunishments(this.prefix(), alias, this);
    }

    @Override
    public SbActivePunishments rename(final String name) {
        return new SbActivePunishments(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbActivePunishments rename(final Name name) {
        return new SbActivePunishments(this.prefix(), name, null);
    }
}
