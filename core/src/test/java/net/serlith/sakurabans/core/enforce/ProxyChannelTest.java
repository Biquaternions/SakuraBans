package net.serlith.sakurabans.core.enforce;

import java.nio.charset.StandardCharsets;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.messaging.ProxyChannel;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@code sakurabans:main} on a backend behind a proxy. */
class ProxyChannelTest extends ServiceTest {

    ProxyChannelTest() {
        super(new FakePlatform().behindProxy(true));
    }

    @Test
    void everyInstanceOfTheDatabaseSharesTheToken() {
        final ProxyChannel other = new ProxyChannel(new MetaRepository(this.storage),
                this.log.logger());
        other.load();

        Assertions.assertThat(this.channel.decode(other.serverName("survival")))
                .contains(new ProxyChannel.Message.ServerName("survival"));
        Assertions.assertThat(this.channel.decode(this.channel.enforce(42)))
                .contains(new ProxyChannel.Message.Enforce(42));
    }

    @Test
    void namesWithoutTheTokenAndGarbageAreIgnored() {
        final byte[] forged = this.channel.serverName("survival");
        final int at = new String(forged, StandardCharsets.ISO_8859_1).indexOf("survival") - 3;
        forged[at] ^= 1;

        Assertions.assertThat(this.channel.decode(forged)).isEmpty();
        Assertions.assertThat(this.channel.decode(new byte[] {1})).isEmpty();
        Assertions.assertThat(this.channel.decode(new byte[] {9, 0})).isEmpty();
        Assertions.assertThat(this.log.entries("warn"))
                .anySatisfy(entry -> Assertions.assertThat(entry.format()).contains("token"));
    }

    @Test
    void bansAskTheProxyBeforeKicking() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        final long id = ((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).build(),
                PunishOptions.defaults()).join()).punishment().id();

        Assertions.assertThat(steve.channelMessages()).singleElement()
                .satisfies(data -> Assertions.assertThat(this.channel.decode(data))
                        .contains(new ProxyChannel.Message.Enforce(id)));
        Assertions.assertThat(steve.disconnectReason()).isPresent();
    }

    @Test
    void serverBansOfOtherServersSendNothing() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).scope(ServerScope.of("survival")).build(),
                PunishOptions.defaults()).join();

        Assertions.assertThat(steve.channelMessages()).isEmpty();
    }
}
