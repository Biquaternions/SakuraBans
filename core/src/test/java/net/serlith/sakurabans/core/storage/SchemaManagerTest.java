package net.serlith.sakurabans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.time.Instant;
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
import net.serlith.sakurabans.core.util.InetAddresses;
import net.serlith.sakurabans.core.util.Uuids;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

class SchemaManagerTest {

    private final RecordingLogger log = new RecordingLogger();
    private final SchemaManager manager = new SchemaManager(this.log.logger());

    @Test
    void createsEveryTableIndexAndTheGlobalScope() {
        final DSLContext dsl = H2Database.create();

        this.manager.prepare(dsl, Optional.empty());

        for (final SbTable<?> table : SakuraTables.all()) {
            assertThat(dsl.fetchCount(table)).as(table.getName()).isNotNegative();
        }
        assertThat(this.manager.version(dsl)).contains(SakuraTables.SCHEMA_VERSION);
        assertThat(dsl.fetchCount(SakuraTables.SCOPES,
                SakuraTables.SCOPES.KIND.eq(ScopeKind.GLOBAL))).isOne();
        assertThat(dsl.meta().getIndexes().stream().map(index -> index.getName()))
                .contains("sb_punishments_victim_ix", "sb_active_punishments_expiry_ix");
    }

    @Test
    void preparingTwiceChangesNothing() {
        final DSLContext dsl = H2Database.create();

        this.manager.prepare(dsl, Optional.empty());
        this.manager.prepare(dsl, Optional.empty());

        assertThat(dsl.fetchCount(SakuraTables.SCOPES)).isOne();
        assertThat(dsl.fetchCount(SakuraTables.META)).isEqualTo(2);
        assertThat(this.log.entries("info").stream().map(RecordingLogger.Entry::format)
                .filter(line -> line.startsWith("Created the database schema"))).hasSize(1);
    }

    @Test
    void refusesDatabasesOfNewerVersions() {
        final DSLContext dsl = H2Database.create();
        this.manager.prepare(dsl, Optional.empty());
        dsl.update(SakuraTables.META).set(SakuraTables.META.V, "2")
                .where(SakuraTables.META.K.eq(SchemaManager.VERSION_KEY)).execute();

        assertThatThrownBy(() -> this.manager.prepare(dsl, Optional.empty()))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("schema version 2");
        assertThat(this.manager.version(dsl)).as("left as it was").contains(2);
    }

    @Test
    void theDatabaseEnforcesVictimShapes() throws Exception {
        final DSLContext dsl = H2Database.create();
        this.manager.prepare(dsl, Optional.empty());
        final byte[] v4 = InetAddresses.toBytes(InetAddress.getByName("203.0.113.7"));

        insertVictim(dsl, VictimKind.PLAYER, UUID.randomUUID(), new byte[0], 0);
        insertVictim(dsl, VictimKind.ADDRESS, Uuids.ZERO, v4, 32);
        insertVictim(dsl, VictimKind.ADDRESS_RANGE, Uuids.ZERO, v4, 24);

        assertThatThrownBy(() -> insertVictim(dsl, VictimKind.PLAYER, UUID.randomUUID(), v4, 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertVictim(dsl, VictimKind.ADDRESS_RANGE, Uuids.ZERO, v4, 33))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertVictim(dsl, VictimKind.ADDRESS, Uuids.ZERO, v4, 32))
                .as("victims are unique").isInstanceOf(DataAccessException.class);
    }

    @Test
    void oneActiveBanPerVictimAndScopeButWarnsStack() {
        final DSLContext dsl = H2Database.create();
        this.manager.prepare(dsl, Optional.empty());
        final long victim = insertVictim(dsl, VictimKind.PLAYER, UUID.randomUUID(), new byte[0], 0);
        final int global = dsl.select(SakuraTables.SCOPES.ID).from(SakuraTables.SCOPES)
                .fetchSingle(SakuraTables.SCOPES.ID);

        activate(dsl, insertPunishment(dsl, PunishmentType.WARN, victim, global), true);
        activate(dsl, insertPunishment(dsl, PunishmentType.WARN, victim, global), true);
        activate(dsl, insertPunishment(dsl, PunishmentType.BAN, victim, global), false);
        final long secondBan = insertPunishment(dsl, PunishmentType.BAN, victim, global);

        assertThatThrownBy(() -> activate(dsl, secondBan, false))
                .isInstanceOf(DataAccessException.class);
        assertThat(dsl.fetchCount(SakuraTables.ACTIVE_PUNISHMENTS)).isEqualTo(3);

        dsl.deleteFrom(SakuraTables.PUNISHMENTS).execute();
        assertThat(dsl.fetchCount(SakuraTables.ACTIVE_PUNISHMENTS)).as("cascade").isZero();
    }

    private static long insertVictim(final DSLContext dsl, final VictimKind kind,
            final UUID uuid, final byte[] address, final int prefixLength) {
        return dsl.insertInto(SakuraTables.VICTIMS)
                .set(SakuraTables.VICTIMS.KIND, kind)
                .set(SakuraTables.VICTIMS.UUID, uuid)
                .set(SakuraTables.VICTIMS.ADDRESS, address)
                .set(SakuraTables.VICTIMS.PREFIX_LENGTH, (short) prefixLength)
                .returningResult(SakuraTables.VICTIMS.ID)
                .fetchSingle()
                .value1();
    }

    private static long insertPunishment(final DSLContext dsl, final PunishmentType type,
            final long victim, final int scope) {
        return dsl.insertInto(SakuraTables.PUNISHMENTS)
                .set(SakuraTables.PUNISHMENTS.TYPE, type)
                .set(SakuraTables.PUNISHMENTS.KIND, type.name().toLowerCase())
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
                .returningResult(SakuraTables.PUNISHMENTS.ID)
                .fetchSingle()
                .value1();
    }

    private static void activate(final DSLContext dsl, final long punishment,
            final boolean stacks) {
        final var row = dsl.selectFrom(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.ID.eq(punishment)).fetchSingle();
        dsl.insertInto(SakuraTables.ACTIVE_PUNISHMENTS)
                .set(SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID, punishment)
                .set(SakuraTables.ACTIVE_PUNISHMENTS.TYPE, row.type())
                .set(SakuraTables.ACTIVE_PUNISHMENTS.VICTIM_ID, row.victimId())
                .set(SakuraTables.ACTIVE_PUNISHMENTS.SCOPE_ID, row.scopeId())
                .set(SakuraTables.ACTIVE_PUNISHMENTS.EXCLUSIVITY_SLOT, stacks ? punishment : 0L)
                .execute();
    }
}
