package net.serlith.sakurabans.core.command.module;

import java.util.EnumSet;
import java.util.List;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentResetRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S67: {@code /sb reset}, the console's bulk delete, deactivation and prune. */
class ResetCommandsTest extends CommandTest {

    @BeforeEach
    void fixture() {
        this.known(STEVE, "Steve");
        this.known(ALEX, "Alex");
        this.run(this.console, "ban Steve -r a");
        this.run(this.console, "mute Steve -r b");
        this.run(this.console, "warn Steve -r c");
        this.run(this.console, "warn Steve -r d");
        this.run(this.console, "kick Steve -r e");
        this.run(this.console, "ban Alex -r f");
        this.run(this.console, "unban Alex");
        this.console.clear();
        this.published.clear();
    }

    private List<Punishment> all() {
        return this.service.query(PunishmentQuery.builder().pageSize(100).build()).join()
                .items();
    }

    private List<Punishment> active() {
        return this.service.query(PunishmentQuery.builder().activeOnly(true).pageSize(100)
                .build()).join().items();
    }

    private void reset(final String arguments) {
        this.run(this.console, "sb reset " + arguments);
        this.run(this.console, "sb reset " + arguments + " --confirm");
    }

    @Test
    void onlyTheConsoleMayReset() {
        final FakeCommandActor admin = FakeCommandActor.player(MOD, "Mod",
                Permissions.ADMIN_SETTINGS, Permissions.PUNISH);

        this.run(admin, "sb reset --mode delete --confirm");

        Assertions.assertThat(admin.last()).contains("don't have permission");
        Assertions.assertThat(this.all()).hasSize(6);
    }

    @Test
    void modesAreRequired() {
        this.run(this.console, "sb reset");

        Assertions.assertThat(this.console.last()).contains("Choose a mode");
        Assertions.assertThat(this.all()).hasSize(6);
    }

    @Test
    void itShowsWhatWouldHappenAndWaitsForTheConfirmation() {
        this.run(this.console, "sb reset --mode delete");

        Assertions.assertThat(this.console.last()).contains("delete (with their history) 6"
                + " punishments (4 active, 2 ended) in all scopes", "--confirm");
        Assertions.assertThat(this.all()).hasSize(6);
        // --confirm alone, for another command line than the one shown, only shows again.
        this.run(this.console, "sb reset --mode prune --confirm");
        Assertions.assertThat(this.console.last()).contains("delete the ended ones among 2");
        Assertions.assertThat(this.all()).hasSize(6);
    }

    @Test
    void deleteRemovesEverythingWithItsHistoryAndIsAuditedOnce() {
        this.reset("--mode delete");

        Assertions.assertThat(this.console.last()).contains("Deleted 6 punishments");
        Assertions.assertThat(this.all()).isEmpty();
        Assertions.assertThat(this.published).anySatisfy(packet -> Assertions.assertThat(packet)
                .isInstanceOf(SyncPacket.PunishmentsResetPacket.class));
        final List<AuditEntry> entries = new AuditRepository(this.storage)
                .about(SubjectType.PUNISHMENT, "all", 10).join();
        Assertions.assertThat(entries).singleElement().satisfies(entry -> {
            Assertions.assertThat(entry.action()).isEqualTo(AuditAction.RESET);
            Assertions.assertThat(entry.newValue()).contains("mode=DELETE", "count=6");
        });
        this.run(this.console, "sb reset --mode delete");
        Assertions.assertThat(this.console.last()).contains("Nothing matches");
    }

    @Test
    void deactivateEndsTheActiveOnesAndKeepsTheHistory() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.mutes.load(STEVE, this.home).join();
        Assertions.assertThat(this.mutes.activeMute(STEVE)).isPresent();
        Assertions.assertThat(steve.disconnectReason()).isEmpty();

        this.reset("--mode deactivate");

        Assertions.assertThat(this.console.last()).contains("Ended 4 active punishments");
        Assertions.assertThat(this.active()).isEmpty();
        Assertions.assertThat(this.all()).hasSize(6);
        Assertions.assertThat(this.all().stream().filter(p -> p.end().isPresent()
                && p.end().get().kind() == EndKind.PRUNED)).hasSize(4);
        Assertions.assertThat(this.mutes.activeMute(STEVE)).isEmpty();
    }

    @Test
    void pruneRemovesOnlyTheEndedOnes() {
        this.reset("--mode prune");

        Assertions.assertThat(this.console.last()).contains("Deleted 2 ended punishments");
        Assertions.assertThat(this.all()).hasSize(4).allSatisfy(p ->
                Assertions.assertThat(p.end()).isEmpty());
    }

    @Test
    void excludedTypesAreLeftAlone() {
        this.reset("--mode delete --exclude ban --exclude mute");

        Assertions.assertThat(this.all()).extracting(Punishment::type)
                .containsExactlyInAnyOrder(PunishmentType.BAN, PunishmentType.BAN,
                        PunishmentType.MUTE);
    }

    @Test
    void scopesLimitIt() {
        this.run(this.console, "ban Alex --server lobby2 -r g");

        this.reset("--mode delete --scope server:lobby2");

        Assertions.assertThat(this.console.last()).contains("Deleted 1 punishments");
        Assertions.assertThat(this.all()).hasSize(6);
    }

    @Test
    void deletedPunishmentsTakeTheirTemplateUsesAlong() {
        this.reset("--mode delete");
        this.templates.create(TemplateBuilder.ladder("toxicity").reason("Toxic")
                .steps(List.of(TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                        TemplateStep.of(PunishmentType.BAN, TemplateDuration.permanent())))
                .build(), CONSOLE).join();
        this.run(this.console, "punish Steve toxicity");
        this.run(this.console, "punish Steve toxicity");
        Assertions.assertThat(this.all()).extracting(Punishment::type)
                .containsExactlyInAnyOrder(PunishmentType.WARN, PunishmentType.BAN);

        this.reset("--mode delete");
        this.run(this.console, "punish Steve toxicity");

        // The ladder starts over: the first step again.
        Assertions.assertThat(this.all()).singleElement().satisfies(p ->
                Assertions.assertThat(p.type()).isEqualTo(PunishmentType.WARN));
    }

    @Test
    void largeTablesAreHandledInBatches() {
        this.reset("--mode delete");
        for (int i = 0; i < 25; i++) {
            this.service.punish(DraftPunishment.builder(PunishmentType.WARN,
                    PlayerVictim.of(STEVE), MODERATOR).reason("w" + i).build(),
                    PunishOptions.defaults()).join();
        }
        final PunishmentResetRepository.Filter filter = new PunishmentResetRepository.Filter(
                EnumSet.allOf(PunishmentType.class), null);
        Assertions.assertThat(new PunishmentResetRepository(this.storage).count(filter).join())
                .isEqualTo(new PunishmentResetRepository.Counts(25, 0));

        final long done = new PunishmentResetRepository(this.storage).run(
                PunishmentResetRepository.Mode.DELETE, filter, CONSOLE, this.now.get(), 7)
                .join();

        Assertions.assertThat(done).isEqualTo(25);
        Assertions.assertThat(this.all()).isEmpty();
    }
}
