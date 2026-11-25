package net.serlith.sakurabans.core.wave;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.event.BanWaveReleasedEvent;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.event.EntityChangedEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.api.wave.BanWaveEntry;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.storage.repository.BanWaveRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Ban waves: drafts, release, schedules and recovery (plan S37c). */
class BanWaveTest extends ServiceTest {

    private static <T> T ok(final AdminResult<T> result) {
        Assertions.assertThat(result).isInstanceOf(AdminResult.Ok.class);
        return ((AdminResult.Ok<T>) result).value();
    }

    private BanWave wave() {
        return ok(this.banWaves.create("cheaters", MODERATOR).join());
    }

    private BanWaveEntry add(final BanWave wave, final UUID victim) {
        return ok(this.banWaves.addEntry(wave.id(), DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(victim), MODERATOR).reason("x-ray").duration(Duration.ofDays(30))
                .scope(ServerScope.of("survival")).build(), PunishOptions.builder()
                .visibility(Visibility.SILENT).build(), MODERATOR).join());
    }

    @Test
    void entriesWaitAndAreReleasedTogetherWithResults() {
        // Steve is banned already, so his entry fails.
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).scope(ServerScope.of("survival")).build(), PunishOptions.defaults())
                .join();
        final BanWave wave = this.wave();
        final BanWaveEntry steve = this.add(wave, STEVE);
        Assertions.assertThat(steve.draft().reason()).contains("x-ray");
        Assertions.assertThat(steve.options().visibility()).isEqualTo(Visibility.SILENT);
        this.add(wave, ALEX);
        Assertions.assertThat(this.banWaves.wave(wave.id()).join()).hasValueSatisfying(found -> {
            Assertions.assertThat(found.entryCount()).isEqualTo(2);
            Assertions.assertThat(found.version()).isEqualTo(3);
        });

        final BanWave released = ok(this.banWaves.release(wave.id(), 3, MODERATOR).join());
        Assertions.assertThat(released.status()).isEqualTo(BanWave.Status.RELEASED);
        Assertions.assertThat(released.releasedAt()).isPresent();
        final List<BanWaveEntry> entries = this.banWaves.entries(wave.id()).join();
        Assertions.assertThat(entries.get(0).result()).hasValueSatisfying(result ->
                Assertions.assertThat(result).isInstanceOfSatisfying(PunishResult.Failure.class,
                        failure -> {
                            Assertions.assertThat(failure.reason()).isEqualTo(
                                    PunishResult.Failure.Reason.ALREADY_PUNISHED);
                            Assertions.assertThat(failure.conflicting()).isPresent();
                        }));
        Assertions.assertThat(entries.get(1).result()).hasValueSatisfying(result ->
                Assertions.assertThat(result).isInstanceOfSatisfying(PunishResult.Success.class,
                        success -> {
                            Assertions.assertThat(success.punishment().visibility())
                                    .isEqualTo(Visibility.SILENT);
                            Assertions.assertThat(success.punishment().scope())
                                    .isEqualTo(ServerScope.of("survival"));
                        }));
        Assertions.assertThat(this.events(BanWaveReleasedEvent.class)).singleElement()
                .satisfies(event -> Assertions.assertThat(event.entries()).hasSize(2));
        Assertions.assertThat(this.published).contains(new SyncPacket.EntityChangedPacket(
                SubjectType.BAN_WAVE, Long.toString(wave.id()), ChangeType.UPDATED,
                released.version(), MODERATOR));

        // Released waves are closed.
        Assertions.assertThat(this.banWaves.addEntry(wave.id(), DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(MOD), MODERATOR).build(),
                PunishOptions.defaults(), MODERATOR).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.banWaves.release(wave.id(), released.version(), MODERATOR)
                .join()).isInstanceOf(AdminResult.ValidationFailed.class);
    }

    @Test
    void versionsCancelsAndRemovals() {
        final BanWave wave = this.wave();
        final BanWaveEntry entry = this.add(wave, STEVE);
        Assertions.assertThat(this.banWaves.cancel(wave.id(), 1, MODERATOR).join())
                .isInstanceOfSatisfying(AdminResult.Conflict.class, conflict ->
                        Assertions.assertThat(conflict.currentVersion()).isEqualTo(2));
        ok(this.banWaves.removeEntry(wave.id(), entry.id(), MODERATOR).join());
        Assertions.assertThat(this.banWaves.removeEntry(wave.id(), entry.id(), MODERATOR)
                .join()).isInstanceOf(AdminResult.NotFound.class);
        final BanWave cancelled = ok(this.banWaves.cancel(wave.id(), 3, MODERATOR).join());
        Assertions.assertThat(cancelled.status()).isEqualTo(BanWave.Status.CANCELLED);
        Assertions.assertThat(this.banWaves.waves(Set.of(BanWave.Status.CANCELLED)).join())
                .hasSize(1);
        Assertions.assertThat(this.events(EntityChangedEvent.BanWaveChanged.class)).hasSize(4);
        Assertions.assertThat(this.banWaves.create(" ", MODERATOR).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
    }

    @Test
    void scheduledWavesAreReleasedOnTimeAndInterruptedOnesFinished() {
        final BanWaveRepository repository = new BanWaveRepository(this.storage);
        final BanWaveScheduler scheduler = new BanWaveScheduler(this.lease, repository,
                this.banWaves, this.executors, this.clock, this.log.logger());
        final BanWave wave = this.wave();
        this.add(wave, STEVE);
        final BanWave scheduled = ok(this.banWaves.schedule(wave.id(),
                this.now.get().plus(Duration.ofHours(1)), 2, MODERATOR).join());
        Assertions.assertThat(scheduled.status()).isEqualTo(BanWave.Status.SCHEDULED);

        scheduler.tick().join();
        Assertions.assertThat(this.banWaves.wave(wave.id()).join().orElseThrow().status())
                .isEqualTo(BanWave.Status.SCHEDULED);
        this.now.set(this.now.get().plus(Duration.ofHours(2)));
        scheduler.tick().join();
        Assertions.assertThat(this.banWaves.wave(wave.id()).join().orElseThrow().status())
                .isEqualTo(BanWave.Status.RELEASED);
        Assertions.assertThat(this.service.activeBan(STEVE, null, ServerScope.of("survival"))
                .join()).hasValueSatisfying(ban -> Assertions.assertThat(ban.source())
                .isEqualTo("in-game"));

        // An instance stopped mid-release: the wave is RELEASING with an entry left.
        final BanWave stuck = this.wave();
        this.add(stuck, ALEX);
        repository.transition(stuck.id(), Set.of(BanWave.Status.DRAFT),
                BanWave.Status.RELEASING, null, null, MODERATOR,
                AuditAction.RELEASE, this.now.get()).join();
        scheduler.tick().join();
        Assertions.assertThat(this.banWaves.wave(stuck.id()).join().orElseThrow().status())
                .isEqualTo(BanWave.Status.RELEASING);
        this.now.set(this.now.get().plus(Duration.ofMinutes(6)));
        scheduler.tick().join();
        Assertions.assertThat(this.banWaves.wave(stuck.id()).join().orElseThrow().status())
                .isEqualTo(BanWave.Status.RELEASED);
        Assertions.assertThat(this.banWaves.entries(stuck.id()).join()).singleElement()
                .satisfies(entry -> Assertions.assertThat(entry.result()).isPresent());
    }
}
