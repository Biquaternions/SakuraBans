package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** S67: {@code /sb addlogin} and the default aliases of {@code /alts} and {@code /warns}. */
class MaintenanceCommandsTest extends CommandTest {

    private static final UUID NEW_PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static InetAddress address(final String text) {
        return InetAddresses.parseLiteral(text).orElseThrow();
    }

    @Test
    void addLoginRecordsTheProfileTheNameAndTheAddress() {
        this.run(this.console, "sb addlogin Newbie " + NEW_PLAYER + " 198.51.100.77");

        Assertions.assertThat(this.console.last()).contains("Recorded a login of Newbie")
                .doesNotContain("198.51.100.77");
        final PlayerProfileView profile = this.players.profile(NEW_PLAYER).join().orElseThrow();
        Assertions.assertThat(profile.name()).isEqualTo("Newbie");
        Assertions.assertThat(profile.lastAddress()).contains(address("198.51.100.77"));
        Assertions.assertThat(this.players.profile("newbie").join()).isPresent();
        Assertions.assertThat(this.players.addresses(NEW_PLAYER).join())
                .extracting(AddressRecord::address).containsExactly(address("198.51.100.77"));
        // The address is hashed with the installation's key like any join: alts find it.
        this.known(STEVE, "Steve");
        this.players.recordConnection(STEVE, "Steve", this.now.get(),
                address("198.51.100.77"), null).join();
        Assertions.assertThat(this.altService.alts(STEVE, AddressStrictness.NORMAL).join())
                .extracting(alt -> alt.account().name()).contains("Newbie");
    }

    @Test
    void addLoginTakesValidNamesAndAddressesAndNeedsItsPermission() {
        this.run(this.console, "sb addlogin bad-name! " + NEW_PLAYER + " 198.51.100.77");
        Assertions.assertThat(this.console.last()).contains("is not a player name");
        this.run(this.console, "sb addlogin Newbie " + NEW_PLAYER + " nowhere");
        Assertions.assertThat(this.console.last()).contains("is not an IP address");
        Assertions.assertThat(this.players.profile(NEW_PLAYER).join()).isEmpty();

        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod", Permissions.PUNISH);
        this.run(mod, "sb addlogin Newbie " + NEW_PLAYER + " 198.51.100.77");
        Assertions.assertThat(mod.last()).contains("don't have permission");
        mod.grant(Permissions.ADMIN_ADDLOGIN);
        this.run(mod, "sb addlogin Newbie " + NEW_PLAYER + " 198.51.100.77");
        Assertions.assertThat(this.players.profile(NEW_PLAYER).join()).isPresent();
    }

    @Test
    void dupeipAndWarningsAreAliasesOfAltsAndWarns() {
        this.known(STEVE, "Steve");
        this.run(this.console, "warn Steve -r first");
        this.console.clear();

        this.run(this.console, "alts Steve");
        final List<String> viaAlts = this.console.texts();
        this.console.clear();
        this.run(this.console, "dupeip Steve");
        Assertions.assertThat(this.console.texts()).isNotEmpty().isEqualTo(viaAlts);

        this.console.clear();
        this.run(this.console, "warns Steve");
        final List<String> viaWarns = this.console.texts();
        this.console.clear();
        this.run(this.console, "warnings Steve");
        Assertions.assertThat(this.console.texts()).isNotEmpty().isEqualTo(viaWarns);
        Assertions.assertThat(String.join("\n", viaWarns)).contains("first");
    }
}
