package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import java.util.List;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PunishCommandsTest extends CommandTest {

    @BeforeEach
    void steve() {
        this.known(STEVE, "Steve");
    }

    private List<Punishment> all() {
        return this.service.query(PunishmentQuery.builder().pageSize(100).build()).join()
                .items();
    }

    private Punishment only() {
        return Assertions.assertThat(this.all()).singleElement().actual();
    }

    @Test
    void bansWithDurationReasonAndReply() {
        final FakeOnlinePlayer online = this.platform.join(STEVE, "Steve", this.home);

        this.run(this.console, "ban Steve -d 7d -r griefing at spawn");

        final Punishment ban = this.only();
        Assertions.assertThat(ban.victim()).isEqualTo(PlayerVictim.of(STEVE));
        Assertions.assertThat(ban.reason()).isEqualTo("griefing at spawn");
        Assertions.assertThat(ban.expiresAt()).contains(this.now.get().plus(Duration.ofDays(7)));
        Assertions.assertThat(ban.operator()).isEqualTo(ConsoleOperator.INSTANCE);
        Assertions.assertThat(ban.source()).isEqualTo("console");
        Assertions.assertThat(this.console.last()).contains("Banned Steve", "griefing at spawn");
        Assertions.assertThat(online.disconnectReason()).isPresent();
    }

    @Test
    void tempbanNeedsDurationAndOnlyTheTemporaryPermission() {
        final FakeCommandActor helper = FakeCommandActor.player(MOD, "Helper",
                Permissions.temporary(PunishmentType.BAN));

        this.run(helper, "ban Steve -r spam");
        Assertions.assertThat(helper.last()).contains("don't have permission");
        this.run(helper, "tempban Steve 1h -r spam");

        Assertions.assertThat(this.only().expiresAt()).isPresent();
        Assertions.assertThat(this.only().operator()).isEqualTo(PlayerOperator.of(MOD, "Helper"));
    }

    @Test
    void theReasonFlagTakesTheRestOfTheLine() {
        this.run(this.console, "ban Steve -d 3d -s -r griefing -o at spawn");

        Assertions.assertThat(this.all()).singleElement().satisfies(ban -> {
            Assertions.assertThat(ban.reason()).isEqualTo("griefing -o at spawn");
            Assertions.assertThat(ban.visibility()).isEqualTo(Visibility.SILENT);
        });
        this.run(this.console, "ban Alex griefing");
        // A bare word is neither a flag nor a reason: Cloud shows the usage.
        Assertions.assertThat(this.console.last()).contains("Usage: /ban <target>");
    }

    @Test
    void eachFlagNeedsItsOwnPermission() {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod",
                Permissions.punish(PunishmentType.MUTE));

        for (final String flag : List.of("-s", "--hide", "-o", "--skip", "-I")) {
            mod.clear();
            this.run(mod, "mute Steve " + flag + " -r spam");
            Assertions.assertThat(mod.last()).as(flag).contains("You can't use the flag");
        }
        Assertions.assertThat(this.all()).isEmpty();

        mod.grant(Permissions.flag(PunishmentType.MUTE, PunishFlags.SILENT));
        this.run(mod, "mute Steve -s -r spam");
        Assertions.assertThat(this.only().visibility()).isEqualTo(Visibility.SILENT);
    }

    @Test
    void silentPunishmentsOnlyReachSilentStaff() {
        final FakeOnlinePlayer normal = this.platform.join(ALEX, "Alex",
                InetAddresses.parseLiteral("198.51.100.1")
                        .orElseThrow()).grant(Permissions.notify(PunishmentType.BAN));

        this.run(this.console, "ban Steve -s -r xray");

        Assertions.assertThat(this.only().visibility()).isEqualTo(Visibility.SILENT);
        Assertions.assertThat(normal.messages()).isEmpty();
        this.run(this.console, "unban Steve");
        this.run(this.console, "ban Steve --hide -r xray");
        Assertions.assertThat(this.service.query(PunishmentQuery.builder().activeOnly(true)
                .build()).join().items().getFirst().visibility()).isEqualTo(Visibility.HIDDEN);
    }

    @Test
    void theSenderFlagIsForTheConsoleOnly() {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod",
                Permissions.punish(PunishmentType.WARN));
        this.run(mod, "warn Steve --sender Discord -r spam");
        Assertions.assertThat(mod.last()).contains("You can't use the flag");

        this.run(this.console, "warn Steve --sender Discord -r spam");
        Assertions.assertThat(this.only().operator()).isEqualTo(CustomOperator.of("Discord", null));

        this.console.clear();
        this.run(this.console, "warn Steve --sender-uuid " + MOD + " -r again");
        Assertions.assertThat(this.console.last()).contains("--sender-uuid needs --sender");
        this.run(this.console, "warn Steve --sender Mod --sender-uuid " + MOD + " -r again");
        Assertions.assertThat(this.all().getFirst().operator())
                .isEqualTo(PlayerOperator.of(MOD, "Mod"));
    }

    @Test
    void reasonsAreRequiredWhenConfigured() {
        // The default config requires none; PunishmentRulesTest covers REASON_REQUIRED itself.
        this.run(this.console, "kick Steve");

        Assertions.assertThat(this.only().reason()).isEqualTo("No reason specified");
    }

    @Test
    void ipVariantsTakeTheLastAddress() {
        this.run(this.console, "ipban Steve -r evading");
        Assertions.assertThat(this.only().victim()).isEqualTo(CompositeVictim.of(STEVE, this.home));

        this.run(this.console, "mute 203.0.113.7 -r spam");
        Assertions.assertThat(this.all().getFirst().victim())
                .isEqualTo(AddressVictim.of(this.home));

        this.console.clear();
        this.run(this.console, "ban 10.0.0.0/8 -r spam");
        Assertions.assertThat(this.console.last()).contains("Banned 10.0.0.0/8");
    }

    @Test
    void scopesComeFromFlagsAndConflictsAreReported() {
        this.run(this.console, "ban Steve --server survival -r hacking");
        Assertions.assertThat(this.only().scope()).isEqualTo(ServerScope.of("survival"));

        this.console.clear();
        this.run(this.console, "ban Steve --server survival -r hacking");
        Assertions.assertThat(this.console.last()).contains("already has an active ban");
        this.run(this.console, "ban Steve --server survival -o -r hacking");
        Assertions.assertThat(this.all()).hasSize(2);
    }

    @Test
    void unknownPlayersAndTemplatesAreExplained() {
        this.run(this.console, "ban Nobody");
        Assertions.assertThat(this.console.last()).contains("Nobody is known as Nobody");

        this.run(this.console, "ban Steve --template spam");
        Assertions.assertThat(this.console.last())
                .contains("no template or reason shortcut called spam");

        this.run(this.console, "ban #3 -r spam");
        Assertions.assertThat(this.console.last()).contains("not a punishment id");
    }
}
