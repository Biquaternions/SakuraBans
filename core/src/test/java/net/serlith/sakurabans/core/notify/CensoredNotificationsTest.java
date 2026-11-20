package net.serlith.sakurabans.core.notify;

import java.net.InetAddress;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CensoredNotificationsTest extends ServiceTest {

    @Override
    protected String configYaml() {
        return "display:\n  censor-ip-addresses: true\n";
    }

    @Test
    void addressesAreCensoredForStaffWithoutViewIps() {
        final String ban = Permissions.notify(PunishmentType.BAN);
        final InetAddress elsewhere = InetAddresses.parseLiteral("198.51.100.1").orElseThrow();
        final FakeOnlinePlayer censored =
                this.platform.join(UUID.randomUUID(), "A", elsewhere).grant(ban);
        final FakeOnlinePlayer viewer = this.platform.join(UUID.randomUUID(), "B", elsewhere)
                .grant(ban, Permissions.VIEW_IPS);

        this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                AddressVictim.of(this.home), CONSOLE).build(), PunishOptions.defaults()).join();

        Assertions.assertThat(plain(censored.messages().getLast()))
                .doesNotContain("203.0.113.7").contains("203.0.");
        Assertions.assertThat(plain(viewer.messages().getLast())).contains("203.0.113.7");
    }
}
