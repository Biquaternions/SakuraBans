package net.serlith.sakurabans.core.storage;

import java.net.InetAddress;
import java.sql.Blob;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.storage.repository.InsertOutcome;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.storage.repository.NewPunishment;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentMapper;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import org.assertj.core.api.Assertions;
import org.h2.api.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * Regression: jOOQ bound {@code byte[]} on H2 as a temporary BLOB. H2 remembers the parameters
 * of a cached subquery result per session, deletes the BLOB {@code LOB_TIMEOUT} after it was
 * made, and the next run of the same query on that connection then failed comparing against it
 * ("This CLOB or BLOB reference timed out"), until one of the queried tables was written.
 */
class H2LobTimeoutTest extends DatabaseTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final long LOB_TIMEOUT_MILLIS = 200;

    @Override
    protected TestDatabase database() {
        // A file database (in memory, H2 keeps LOBs differently) and one pooled connection, so
        // every query runs in the same H2 session.
        final String yaml =
                """
                type: H2
                h2:
                  file: sakurabans
                properties:
                  LOB_TIMEOUT: '%d'
                address-hash-key: 'h2-lob-timeout-test-address-hash-key-0123456789'
                pool:
                  maximum-size: 1
                  minimum-idle: 1
                """;
        return () -> Optional.of(yaml.formatted(LOB_TIMEOUT_MILLIS));
    }

    @Test
    void cachedSubqueriesSurviveTheLobTimeout() throws Exception {
        final PunishmentRepository punishments = new PunishmentRepository(this.storage,
                this.hasher, new PunishmentMapper(Long::toString));
        final InetAddress address = InetAddress.getByName("203.0.113.7");
        new PlayerRepository(this.storage, this.hasher)
                .recordConnection(STEVE, "Steve", this.now.get(), address, null).join();
        final NewPunishment mute = new NewPunishment(PunishmentType.MUTE, false,
                PlayerVictim.of(STEVE), GlobalScope.INSTANCE, "reason",
                Actor.of(ConsoleOperator.INSTANCE, "console"), this.now.get(), null, null,
                Visibility.NORMAL, null, null, List.of(), null);
        final Punishment muted = ((InsertOutcome.Inserted) punishments.insert(mute,
                OverridePolicy.REJECT).join()).punishment();
        final Applicability applicability =
                Applicability.of(STEVE, address, AddressStrictness.NORMAL);

        for (int run = 0; run < 2; run++) {
            Assertions.assertThat(punishments.activeApplicable(PunishmentType.MUTE,
                    applicability, List.of(GlobalScope.INSTANCE), this.tick()).join())
                    .containsExactly(muted);
        }
        // A LOB of our own, younger than any the queries bound, on the same connection.
        final Blob probe = this.read(dsl -> dsl.connectionResult(connection -> {
            final Blob blob = connection.createBlob();
            blob.setBytes(1, new byte[] {1, 2, 3});
            return blob;
        }));
        Thread.sleep(LOB_TIMEOUT_MILLIS * 3);
        // A commit, where H2 schedules the timed-out LOBs for removal; not on a table the
        // query reads, which would drop the cached results.
        new MetaRepository(this.storage).put("lob_timeout_test", "1").join();
        // H2 removes them in the background once the store moved on, oldest first: the probe
        // gone means the queries' are too.
        DatabaseTest.eventually(() -> this.read(dsl -> removed(probe)));
        for (int run = 0; run < 2; run++) {
            Assertions.assertThat(punishments.activeApplicable(PunishmentType.MUTE,
                    applicability, List.of(GlobalScope.INSTANCE), this.tick()).join())
                    .as("run %d after the timeout", run).containsExactly(muted);
        }
    }

    private static boolean removed(final Blob blob) {
        try {
            blob.getBytes(1, 3);
            return false;
        } catch (final SQLException e) {
            Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOB_CLOSED_ON_TIMEOUT_1);
            return true;
        }
    }

    // A new instant per query, as the mute cache's refreshes have.
    private Instant tick() {
        return this.now.updateAndGet(instant -> instant.plusSeconds(1));
    }
}
