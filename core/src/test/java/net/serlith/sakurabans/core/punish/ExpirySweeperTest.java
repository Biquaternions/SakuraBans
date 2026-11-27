package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.util.UUID;
import net.serlith.sakurabans.api.event.PunishmentExpiredEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.storage.Lease;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ExpirySweeperTest extends ServiceTest {

    private void punish(final UUID victim, final Duration duration) {
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(victim),
                MODERATOR).duration(duration).build(), PunishOptions.defaults()).join();
    }

    @Test
    void endsOnlyWhatIsDueAndOnlyOnce() {
        this.punish(STEVE, Duration.ofMinutes(1));
        this.punish(ALEX, Duration.ofHours(1));
        this.now.set(this.now.get().plus(Duration.ofMinutes(2)));

        Assertions.assertThat(this.sweeper.sweep().join()).isOne();
        Assertions.assertThat(this.sweeper.sweep().join()).isZero();
        Assertions.assertThat(this.events(PunishmentExpiredEvent.class)).hasSize(1);
    }

    @Test
    void onlyTheLeaseHolderSweeps() {
        this.punish(STEVE, Duration.ofMinutes(1));
        this.now.set(this.now.get().plus(Duration.ofMinutes(2)));
        final Lease other = new Lease(this.storage, UUID.randomUUID());
        Assertions.assertThat(other.tryAcquire("sweeper", Duration.ofMinutes(5)).join()).isTrue();

        Assertions.assertThat(this.sweeper.sweep().join()).isZero();
        // The lease goes by the database's clock: a host clock past its end doesn't free it.
        this.now.set(this.now.get().plus(Duration.ofMinutes(6)));
        Assertions.assertThat(this.sweeper.sweep().join()).isZero();

        other.release("sweeper").join();
        Assertions.assertThat(this.sweeper.sweep().join()).isOne();
    }

    @Test
    void dropsMuteCacheEntriesOfPlayersWhoLeft() {
        this.mutes.load(STEVE, this.home).join();
        this.platform.join(ALEX, "Alex", this.home);
        this.mutes.load(ALEX, this.home).join();
        this.now.set(this.now.get().plus(Duration.ofMinutes(2)));

        this.sweeper.sweep().join();

        Assertions.assertThat(this.mutes.loaded(STEVE)).isFalse();
        Assertions.assertThat(this.mutes.loaded(ALEX)).isTrue();
    }
}
