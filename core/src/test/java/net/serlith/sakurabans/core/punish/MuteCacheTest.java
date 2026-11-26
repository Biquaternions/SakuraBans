package net.serlith.sakurabans.core.punish;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.repository.AltPolicy;
import net.serlith.sakurabans.core.storage.repository.InsertOutcome;
import net.serlith.sakurabans.core.storage.repository.NewPunishment;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentMapper;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MuteCacheTest extends DatabaseTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    private PunishmentRepository punishments;
    private MuteCache cache;
    private InetAddress home;

    @Override
    protected String configYaml() {
        return "server:\n  name: lobby\n";
    }

    @BeforeEach
    void create() throws Exception {
        this.home = InetAddress.getByName("203.0.113.7");
        this.punishments =
                new PunishmentRepository(this.storage, new PunishmentMapper(Long::toString));
        final SakuraScopeService scopes =
                new SakuraScopeService(this.config, new FakePlatform(), false,
                        this.log.logger());
        this.cache = new MuteCache(this.punishments, scopes, this.config, this.clock,
                new ActiveRangeCache(this.punishments, new PlayerRepository(this.storage),
                        AltPolicy.NONE, this.clock));
    }

    private Punishment mute(final Victim victim, final Scope scope,
            final @Nullable Duration duration) {
        final Instant at = this.now.get();
        final InsertOutcome outcome = this.punishments.insert(new NewPunishment(
                PunishmentType.MUTE, "mute", false, null, victim, scope, "spam", CONSOLE, at,
                duration == null ? null : at.plus(duration), null, Visibility.NORMAL, null, null,
                List.of(), null), OverridePolicy.REJECT).join();
        return ((InsertOutcome.Inserted) outcome).punishment();
    }

    @Test
    void unknownUntilLoaded() {
        Assertions.assertThat(this.cache.lookup(STEVE))
                .isInstanceOf(MuteCache.Lookup.Unknown.class);

        this.cache.load(STEVE, this.home).join();

        Assertions.assertThat(this.cache.lookup(STEVE))
                .isInstanceOf(MuteCache.Lookup.NotMuted.class);
        Assertions.assertThat(this.cache.activeMute(STEVE)).isEmpty();
    }

    @Test
    void loadsMutesOfThisServersScopesAndAddress() {
        this.mute(PlayerVictim.of(STEVE), ServerScope.of("survival"), null);
        Assertions.assertThat(this.cache.load(STEVE, this.home)
                .thenApply(ignored -> this.cache.activeMute(STEVE)).join()).isEmpty();

        final Punishment byAddress = this.mute(AddressVictim.of(this.home),
                ServerScope.of("lobby"), null);
        this.cache.reload(STEVE).join();

        Assertions.assertThat(this.cache.activeMute(STEVE)).contains(byAddress);
    }

    @Test
    void proxiesLoadEveryScopeAndApplyThoseOfTheCurrentServer() {
        final FakePlatform platform = new FakePlatform(Set.of("lobby", "survival"), List.of());
        final MuteCache proxy = new MuteCache(this.punishments,
                new SakuraScopeService(this.config, platform, true, this.log.logger()),
                this.config, this.clock, new ActiveRangeCache(this.punishments,
                        new PlayerRepository(this.storage), AltPolicy.NONE, this.clock));
        final Punishment survival = this.mute(PlayerVictim.of(STEVE), ServerScope.of("survival"),
                null);
        final FakeOnlinePlayer steve = platform.join(STEVE, "Steve", this.home, null, "lobby");
        proxy.load(STEVE, this.home).join();

        Assertions.assertThat(proxy.lookup(steve)).isInstanceOf(MuteCache.Lookup.NotMuted.class);
        Assertions.assertThat(proxy.activeMute(STEVE)).isEmpty();

        steve.connect("survival").join();

        Assertions.assertThat(proxy.lookup(steve)).isEqualTo(new MuteCache.Lookup.Muted(survival));
        Assertions.assertThat(proxy.activeMute(STEVE)).contains(survival);
    }

    @Test
    void mutesThatRunOutAreIgnoredWithoutReload() {
        this.mute(PlayerVictim.of(STEVE), GlobalScope.INSTANCE, Duration.ofMinutes(5));
        this.cache.load(STEVE, this.home).join();
        Assertions.assertThat(this.cache.activeMute(STEVE)).isPresent();

        this.now.set(this.now.get().plus(Duration.ofMinutes(5)));

        Assertions.assertThat(this.cache.lookup(STEVE))
                .isInstanceOf(MuteCache.Lookup.NotMuted.class);
    }

    @Test
    void endedMutesDontComeBackWithLateLoads() {
        final Punishment mute = this.mute(PlayerVictim.of(STEVE), GlobalScope.INSTANCE, null);
        this.cache.load(STEVE, this.home).join();

        this.cache.ended(mute.id());
        Assertions.assertThat(this.cache.activeMute(STEVE)).isEmpty();

        // The row is still active (as if the end hadn't committed when this load read it).
        this.cache.load(STEVE, this.home).join();
        Assertions.assertThat(this.cache.activeMute(STEVE)).isEmpty();

        this.punishments.end(mute.id(), EndKind.REVOKED, this.now.get(), CONSOLE, null).join();
    }

    @Test
    void forgetsPlayersThatLeft() {
        this.cache.load(STEVE, this.home).join();
        final UUID other = UUID.randomUUID();
        this.cache.load(other, this.home).join();

        this.cache.invalidate(other);
        this.cache.retainOnly(Set.of(), this.now.get());
        Assertions.assertThat(this.cache.loaded(STEVE)).isTrue();

        this.cache.retainOnly(Set.of(), this.now.get().plusSeconds(1));
        Assertions.assertThat(this.cache.loaded(STEVE)).isFalse();
        Assertions.assertThat(this.cache.loaded(other)).isFalse();
    }
}
