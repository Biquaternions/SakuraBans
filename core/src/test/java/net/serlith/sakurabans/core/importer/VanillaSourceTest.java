package net.serlith.sakurabans.core.importer;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbPunishmentsRecord;
import org.assertj.core.api.Assertions;
import org.jooq.Record3;
import org.junit.jupiter.api.Test;

class VanillaSourceTest extends ImportTest {

    private static final UUID EXPIRED = UUID.fromString("0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b");
    private static final UUID LONG = UUID.fromString("9a3b1c2d-0000-4000-8000-000000000001");

    @Override
    protected ImportSources sources() {
        return new ImportSources(List.of(new VanillaSource()));
    }

    private void files() throws IOException {
        final Path players = Files.writeString(this.dir.resolve("banned-players.json"),
                resource("vanilla/banned-players.json"));
        final Path ips = Files.writeString(this.dir.resolve("banned-ips.json"),
                resource("vanilla/banned-ips.json"));
        this.vanilla(players, ips);
    }

    private SbPunishmentsRecord stored(final String externalId) {
        return this.read(dsl -> dsl.selectFrom(SakuraTables.PUNISHMENTS)
                .where(SakuraTables.PUNISHMENTS.EXTERNAL_ID.eq(externalId)).fetchSingle());
    }

    private Record3<VictimKind, UUID, byte[]> victim(final SbPunishmentsRecord punishment) {
        return this.read(dsl -> dsl.select(SakuraTables.VICTIMS.KIND, SakuraTables.VICTIMS.UUID,
                        SakuraTables.VICTIMS.ADDRESS).from(SakuraTables.VICTIMS)
                .where(SakuraTables.VICTIMS.ID.eq(punishment.victimId())).fetchSingle());
    }

    @Test
    void bansAreImportedOnceWithOperatorsTimesAndNames() throws IOException {
        this.files();

        Assertions.assertThat(this.jobs().survey("vanilla").join())
                .isEqualTo(new ImportOutcome.Surveyed(Map.of("banned-players.json", 6L,
                        "banned-ips.json", 4L)));
        final ImportReport report = this.importFrom("vanilla");

        Assertions.assertThat(report.imported()).isEqualTo(7);
        Assertions.assertThat(report.failed()).isEqualTo(3);
        Assertions.assertThat(report.players()).isEqualTo(4);
        Assertions.assertThat(report.truncated()).isEqualTo(1);
        Assertions.assertThat(report.failures()).extracting(ImportReport.Failure::externalId)
                .containsExactlyInAnyOrder("vanilla:banned-players.json:not-a-uuid",
                        "vanilla:banned-players.json:9a3b1c2d-0000-4000-8000-000000000002",
                        "vanilla:banned-ips.json:not an address");

        final SbPunishmentsRecord steve = this.stored(
                "vanilla:players:" + STEVE + ":" + Instant.parse("2026-10-01T12:00:00Z")
                        .getEpochSecond());
        Assertions.assertThat(steve.operatorKind()).isEqualTo(OperatorKind.CONSOLE);
        Assertions.assertThat(steve.expiresAt()).isNull();
        Assertions.assertThat(steve.endKind()).isNull();
        Assertions.assertThat(steve.reason()).isEqualTo("Griefing the spawn");
        Assertions.assertThat(steve.createdAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        Assertions.assertThat(this.victim(steve).value2()).isEqualTo(STEVE);

        final SbPunishmentsRecord alex = this.stored(
                "vanilla:players:" + ALEX + ":" + Instant.parse("2026-10-20T06:30:00Z")
                        .getEpochSecond());
        Assertions.assertThat(alex.operatorKind()).isEqualTo(OperatorKind.CUSTOM);
        Assertions.assertThat(alex.operatorName()).isEqualTo("Herobrine");
        Assertions.assertThat(alex.operatorUuid()).isNull();
        Assertions.assertThat(alex.expiresAt()).isEqualTo(Instant.parse("2099-01-01T00:00:00Z"));
        Assertions.assertThat(alex.endKind()).isNull();

        final SbPunishmentsRecord expired = this.stored(
                "vanilla:players:" + EXPIRED + ":" + Instant.parse("2026-01-01T00:00:00Z")
                        .getEpochSecond());
        Assertions.assertThat(expired.endKind()).isEqualTo(EndKind.EXPIRED);
        Assertions.assertThat(expired.endedAt()).isEqualTo(Instant.parse("2026-01-08T00:00:00Z"));
        Assertions.assertThat(expired.operatorKind()).isEqualTo(OperatorKind.CONSOLE);

        final SbPunishmentsRecord longReason = this.stored(
                "vanilla:players:" + LONG + ":" + Instant.parse("2026-10-02T00:00:00Z")
                        .getEpochSecond());
        Assertions.assertThat(longReason.reason()).hasSize(1024);

        final SbPunishmentsRecord address = this.stored("vanilla:ips:203.0.113.50:"
                + Instant.parse("2026-10-03T10:00:00Z").getEpochSecond());
        final Record3<VictimKind, UUID, byte[]> victim = this.victim(address);
        Assertions.assertThat(victim.value1()).isEqualTo(VictimKind.ADDRESS);
        Assertions.assertThat(victim.value3())
                .isEqualTo(InetAddress.getByAddress(new byte[] {(byte) 203, 0, 113, 50})
                        .getAddress());
        final SbPunishmentsRecord v6 = this.stored("vanilla:ips:2001:db8::1:"
                + Instant.parse("2026-10-04T10:00:00Z").getEpochSecond());
        Assertions.assertThat(v6.operatorName()).isEqualTo("Herobrine");
        Assertions.assertThat(v6.endKind()).isNull();
        Assertions.assertThat(this.stored("vanilla:ips:198.51.100.9:"
                + Instant.parse("2026-02-01T10:00:00Z").getEpochSecond()).endKind())
                .isEqualTo(EndKind.EXPIRED);

        final String name = this.read(dsl -> dsl.select(SakuraTables.PLAYER_NAMES.NAME)
                .from(SakuraTables.PLAYER_NAMES)
                .where(SakuraTables.PLAYER_NAMES.UUID.eq(STEVE))
                .fetchSingle().value1());
        Assertions.assertThat(name).isEqualTo("Steve");
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(7);

        final ImportReport second = this.importFrom("vanilla");
        Assertions.assertThat(second.imported()).isZero();
        Assertions.assertThat(second.skipped()).isEqualTo(7);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(7);
    }

    @Test
    void dryRunsWriteNothing() throws IOException {
        this.files();

        final ImportReport report = this.importFrom("vanilla", true);

        Assertions.assertThat(report.dryRun()).isTrue();
        Assertions.assertThat(report.imported()).isEqualTo(7);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
        Assertions.assertThat(this.count(SakuraTables.PLAYER_NAMES)).isZero();
    }

    @Test
    void missingFilesAreNotAnError() {
        this.vanilla(this.dir.resolve("none-players.json"), this.dir.resolve("none-ips.json"));

        Assertions.assertThat(this.jobs().survey("vanilla").join())
                .isEqualTo(new ImportOutcome.Surveyed(Map.of("banned-players.json", 0L,
                        "banned-ips.json", 0L)));
        Assertions.assertThat(this.importFrom("vanilla").imported()).isZero();
    }

    @Test
    void malformedJsonStopsTheImportClearly() throws IOException {
        final Path players = Files.writeString(this.dir.resolve("banned-players.json"),
                "[{\"uuid\": ");
        final Path ips = Files.writeString(this.dir.resolve("banned-ips.json"), "{}");
        this.vanilla(players, ips);

        Assertions.assertThat(this.outcome("vanilla", false))
                .isInstanceOfSatisfying(ImportOutcome.Refused.class, refused -> {
                    Assertions.assertThat(refused.problem())
                            .isEqualTo(ImportOutcome.Problem.FAILED);
                    Assertions.assertThat(refused.detail()).contains("banned-players.json");
                });
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
    }
}
