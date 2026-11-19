package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbVictimsRecord;
import org.jooq.Condition;
import org.jooq.Constraint;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbVictims extends SbTable<SbVictimsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbVictimsRecord, Long> ID = createField(
            DSL.name("id"), SQLDataType.BIGINT.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbVictimsRecord, VictimKind> KIND = createField(
            DSL.name("kind"), SbTypes.VICTIM_KIND.nullable(false), this, "");

    public final TableField<SbVictimsRecord, UUID> UUID = createField(
            DSL.name("uuid"), SbTypes.UUID.nullable(false), this, "");

    public final TableField<SbVictimsRecord, byte[]> ADDRESS = createField(
            DSL.name("address"), SQLDataType.VARBINARY(16).nullable(false), this, "");

    public final TableField<SbVictimsRecord, Short> PREFIX_LENGTH = createField(
            DSL.name("prefix_length"), SQLDataType.SMALLINT.nullable(false), this, "");

    public SbVictims(final String prefix) {
        this(prefix, DSL.name(prefix + "victims"), null);
    }

    private SbVictims(final String prefix, final Name alias,
            final @Nullable Table<SbVictimsRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbVictimsRecord> getRecordType() {
        return SbVictimsRecord.class;
    }

    @Override
    public UniqueKey<SbVictimsRecord> getPrimaryKey() {
        return Keys.VICTIMS_PK;
    }

    @Override
    public List<UniqueKey<SbVictimsRecord>> getUniqueKeys() {
        return List.of(Keys.VICTIMS_IDENTITY_UQ);
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.VICTIMS_UUID_IX,
                Indexes.VICTIMS_ADDRESS_IX);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("shape_ck",
                        this.KIND.eq(VictimKind.PLAYER)
                                .and(octets(this.ADDRESS).eq(0))
                                .and(this.PREFIX_LENGTH.eq((short) 0))
                                .or(this.KIND.in(VictimKind.ADDRESS, VictimKind.COMPOSITE)
                                .and(this.prefixIs(4, 32, 32).or(this.prefixIs(16, 128, 128))))
                                .or(this.KIND.eq(VictimKind.ADDRESS_RANGE)
                                .and(this.prefixIs(4, 1, 32).or(this.prefixIs(16, 1, 128))))));
    }

    private Condition prefixIs(final int bytes, final int min, final int max) {
        return octets(this.ADDRESS).eq(bytes)
                .and(this.PREFIX_LENGTH.between((short) min, (short) max));
    }

    @Override
    public SbVictims as(final String alias) {
        return new SbVictims(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbVictims as(final Name alias) {
        return new SbVictims(this.prefix(), alias, this);
    }

    @Override
    public SbVictims rename(final String name) {
        return new SbVictims(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbVictims rename(final Name name) {
        return new SbVictims(this.prefix(), name, null);
    }
}
