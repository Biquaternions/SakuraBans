package net.serlith.sakurabans.core.storage;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.ScopeKind;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import net.serlith.sakurabans.core.util.AddressHashes;
import net.serlith.sakurabans.core.util.InetAddresses;
import net.serlith.sakurabans.core.util.Uuids;
import org.assertj.core.api.Assertions;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The storage service already prepared the schema once when each test starts. */
class SchemaManagerTest extends DatabaseTest {

    private final SchemaManager manager = new SchemaManager(this.log.logger());

    @Test
    void createsEveryTableIndexAndTheGlobalScope() {
        for (final SbTable<?> table : SakuraTables.all()) {
            Assertions.assertThat(this.count(table)).as(table.getName())
                    .isNotNegative();
        }
        final Optional<Integer> version = this.read(this.manager::version);
        Assertions.assertThat(version).contains(SakuraTables.SCHEMA_VERSION);
        Assertions.assertThat(this.count(SakuraTables.SCOPES,
                SakuraTables.SCOPES.KIND.eq(ScopeKind.GLOBAL))).isOne();
    }

    @Test
    void preparingAgainChangesNothing() {
        this.read(dsl -> {
            this.manager.prepare(dsl, this.schema().orElse(null));
            return null;
        });

        Assertions.assertThat(this.count(SakuraTables.SCOPES)).isOne();
        Assertions.assertThat(this.count(SakuraTables.META)).isEqualTo(2);
        Assertions.assertThat(this.log.entries("info").stream().map(RecordingLogger.Entry::format)
                .filter(line -> line.startsWith("Created the database schema"))).hasSize(1);
    }

    @Test
    void createsTheIndexes() {
        final List<String> indexes = this.read(dsl -> dsl.meta().getIndexes().stream()
                .map(index -> index.getName().toLowerCase(Locale.ROOT)).toList());
        Assertions.assertThat(indexes).contains("sb_punishments_victim_ix",
                "sb_active_punishments_expiry_ix");
    }

    @Test
    void refusesDatabasesOfNewerVersions() {
        this.read(dsl -> dsl.update(SakuraTables.META).set(SakuraTables.META.V, "2")
                .where(SakuraTables.META.K.eq(SchemaManager.VERSION_KEY)).execute());

        Assertions.assertThatThrownBy(() -> this.read(dsl -> {
            this.manager.prepare(dsl, this.schema().orElse(null));
            return null;
        })).hasCauseInstanceOf(StorageException.class)
                .hasMessageContaining("schema version 2");
        final Optional<Integer> version = this.read(this.manager::version);
        Assertions.assertThat(version).as("left as it was").contains(2);
    }

    @Test
    void theDatabaseEnforcesVictimShapes() throws Exception {
        final byte[] v4 = InetAddresses.toBytes(InetAddress.getByName("203.0.113.7"));

        this.read(dsl -> insertVictim(dsl, VictimKind.PLAYER, UUID.randomUUID(), new byte[0], 0));
        this.read(dsl -> insertVictim(dsl, VictimKind.ADDRESS, Uuids.ZERO, v4, 32));
        this.read(dsl -> insertVictim(dsl, VictimKind.ADDRESS_RANGE, Uuids.ZERO, v4, 24));

        Assertions.assertThatThrownBy(() -> this.read(dsl -> insertVictim(dsl, VictimKind.PLAYER,
                UUID.randomUUID(), v4, 0))).hasCauseInstanceOf(DataAccessException.class);
        Assertions.assertThatThrownBy(() -> this.read(dsl -> insertVictim(dsl,
                VictimKind.ADDRESS_RANGE,
                Uuids.ZERO, v4, 33))).hasCauseInstanceOf(DataAccessException.class);
        Assertions.assertThatThrownBy(() -> this.read(dsl -> insertVictim(dsl, VictimKind.ADDRESS,
                Uuids.ZERO, v4, 32))).as("victims are unique")
                .hasCauseInstanceOf(DataAccessException.class);

        // An erased victim has its hash and prefix but no address.
        final byte[] hash = AddressHashes.of(InetAddress.getByName("198.51.100.9"));
        this.read(dsl -> insertVictim(dsl, VictimKind.COMPOSITE, UUID.randomUUID(), null, 32,
                hash));
        Assertions.assertThatThrownBy(() -> this.read(dsl -> insertVictim(dsl,
                VictimKind.ADDRESS, Uuids.ZERO, null, 0, hash)))
                .hasCauseInstanceOf(DataAccessException.class);
        Assertions.assertThatThrownBy(() -> this.read(dsl -> insertVictim(dsl,
                VictimKind.ADDRESS_RANGE, Uuids.ZERO, null, 24, hash)))
                .hasCauseInstanceOf(DataAccessException.class);
    }

    @Test
    void oneActiveBanPerVictimAndScopeButWarnsStack() {
        final long victim = this.read(dsl -> insertVictim(dsl, VictimKind.PLAYER,
                UUID.randomUUID(), new byte[0], 0));
        final int global = this.read(dsl -> dsl.select(SakuraTables.SCOPES.ID)
                .from(SakuraTables.SCOPES).fetchSingle(SakuraTables.SCOPES.ID));

        this.read(dsl -> activate(dsl,
                insertPunishment(dsl, PunishmentType.WARN, victim, global), true));
        this.read(dsl -> activate(dsl,
                insertPunishment(dsl, PunishmentType.WARN, victim, global), true));
        this.read(dsl -> activate(dsl,
                insertPunishment(dsl, PunishmentType.BAN, victim, global), false));
        final long secondBan =
                this.read(dsl -> insertPunishment(dsl, PunishmentType.BAN, victim, global));

        Assertions.assertThatThrownBy(() -> this.read(dsl -> activate(dsl, secondBan, false)))
                .hasCauseInstanceOf(DataAccessException.class);
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS))
                .isEqualTo(3);

        this.read(dsl -> dsl.deleteFrom(SakuraTables.PUNISHMENTS).execute());
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS))
                .as("cascade").isZero();
    }

    private static long insertVictim(final DSLContext dsl, final VictimKind kind,
            final UUID uuid, final byte[] address, final int prefixLength) {
        return insertVictim(dsl, kind, uuid, address, prefixLength, address.length == 0
                ? new byte[AddressHashes.LENGTH]
                : AddressHashes.of(InetAddresses.fromBytes(address)));
    }

    private static long insertVictim(final DSLContext dsl, final VictimKind kind,
            final UUID uuid, final byte @Nullable [] address, final int prefixLength,
            final byte[] hash) {
        return dsl.insertInto(SakuraTables.VICTIMS)
                .set(SakuraTables.VICTIMS.KIND, kind)
                .set(SakuraTables.VICTIMS.UUID, uuid)
                .set(SakuraTables.VICTIMS.ADDRESS, address)
                .set(SakuraTables.VICTIMS.PREFIX_LENGTH, (short) prefixLength)
                .set(SakuraTables.VICTIMS.ADDRESS_HASH, hash)
                .returningResult(SakuraTables.VICTIMS.ID)
                .fetchSingle()
                .value1();
    }

    private static long insertPunishment(final DSLContext dsl, final PunishmentType type,
            final long victim, final int scope) {
        return dsl.insertInto(SakuraTables.PUNISHMENTS)
                .set(SakuraTables.PUNISHMENTS.TYPE, type)
                .set(SakuraTables.PUNISHMENTS.SHADOW, false)
                .set(SakuraTables.PUNISHMENTS.VICTIM_ID, victim)
                .set(SakuraTables.PUNISHMENTS.SCOPE_ID, scope)
                .set(SakuraTables.PUNISHMENTS.REASON, "")
                .set(SakuraTables.PUNISHMENTS.OPERATOR_KIND, OperatorKind.CONSOLE)
                .set(SakuraTables.PUNISHMENTS.OPERATOR_NAME, "Console")
                .set(SakuraTables.PUNISHMENTS.SOURCE, "console")
                .set(SakuraTables.PUNISHMENTS.CREATED_AT, Instant.ofEpochMilli(1000))
                .set(SakuraTables.PUNISHMENTS.VISIBILITY, Visibility.NORMAL)
                .set(SakuraTables.PUNISHMENTS.VERSION, 1L)
                .set(SakuraTables.PUNISHMENTS.ACKNOWLEDGED, false)
                .returningResult(SakuraTables.PUNISHMENTS.ID)
                .fetchSingle()
                .value1();
    }

    private static int activate(final DSLContext dsl, final long punishment,
            final boolean stacks) {
        final SbPunishmentsRecord row = dsl.selectFrom(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.ID.eq(punishment)).fetchSingle();
        return dsl.insertInto(SakuraTables.ACTIVE_PUNISHMENTS)
                .set(SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID, punishment)
                .set(SakuraTables.ACTIVE_PUNISHMENTS.TYPE, row.type())
                .set(SakuraTables.ACTIVE_PUNISHMENTS.VICTIM_ID, row.victimId())
                .set(SakuraTables.ACTIVE_PUNISHMENTS.SCOPE_ID, row.scopeId())
                .set(SakuraTables.ACTIVE_PUNISHMENTS.EXCLUSIVITY_SLOT, stacks ? punishment : 0L)
                .execute();
    }
}
