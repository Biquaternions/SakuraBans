package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Template commands: {@code -t} per type, {@code /punish}, {@code /sb templates reset}. */
class LadderCommandsTest extends CommandTest {

    @BeforeEach
    void templates() {
        this.known(STEVE, "Steve");
        this.templates.create(TemplateBuilder.ladder("misconduct").reason("Misconduct")
                .steps(List.of(TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                        TemplateStep.none(List.of("say {victim} is on probation")),
                        TemplateStep.of(PunishmentType.MUTE,
                                TemplateDuration.fixed(Duration.ofDays(1)))))
                .build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.ladder("secret").reason("Secret")
                .permissionRequired(true)
                .steps(List.of(TemplateStep.of(PunishmentType.BAN, TemplateDuration.permanent())))
                .build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("spam", PunishmentType.MUTE)
                .reason("Spam").build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("hacking", PunishmentType.BAN)
                .reason("Hacking").duration(TemplateDuration.fixed(Duration.ofDays(30)))
                .build(), CONSOLE).join();
    }

    private List<Punishment> all() {
        return this.service.query(PunishmentQuery.builder().pageSize(100).build()).join()
                .items();
    }

    @Test
    void punishAppliesTheNextStepAndSaysWhatHappened() {
        this.run(this.console, "punish Steve misconduct");
        Assertions.assertThat(this.console.last()).contains("Warned Steve", "Misconduct");

        this.run(this.console, "punish Steve misconduct -r swearing");
        Assertions.assertThat(this.console.last())
                .contains("Applied step 2 of misconduct to Steve", "only its actions");
        Assertions.assertThat(this.platform.consoleCommands())
                .containsExactly("say Steve is on probation");

        this.run(this.console, "punish Steve misconduct -s --server lobby -r spamming");
        Assertions.assertThat(this.console.last()).contains("Muted Steve", "spamming");
        Assertions.assertThat(this.all()).extracting(Punishment::type)
                .containsExactlyInAnyOrder(PunishmentType.WARN, PunishmentType.MUTE);
        Assertions.assertThat(this.all()).filteredOn(p -> p.type() == PunishmentType.MUTE)
                .singleElement().satisfies(mute -> {
                    Assertions.assertThat(mute.scope()).isEqualTo(ServerScope.of("lobby"));
                    Assertions.assertThat(mute.visibility()).isEqualTo(Visibility.SILENT);
                    Assertions.assertThat(mute.template().orElseThrow().step()).hasValue(2);
                });
    }

    @Test
    void punishTakesLaddersOnlyWithItsOwnPermissions() {
        this.run(this.console, "punish Steve nothing");
        Assertions.assertThat(this.console.last()).contains("no ladder called nothing");
        this.run(this.console, "punish Steve spam");
        Assertions.assertThat(this.console.last())
                .contains("spam is a mute template, not a ladder", "/mute Steve -t spam");

        // Only sakurabans.punish: no ban, warn or mute permission is needed.
        final FakeCommandActor helper = FakeCommandActor.player(MOD, "Helper",
                Permissions.PUNISH);
        this.platform.join(MOD, "Helper", this.home).grant(Permissions.PUNISH);
        this.run(helper, "punish Steve misconduct");
        Assertions.assertThat(helper.last()).contains("Warned Steve");
        this.run(helper, "punish Steve secret");
        Assertions.assertThat(helper.last()).contains("can't use the template secret");
        this.run(helper, "punish Steve misconduct -s");
        Assertions.assertThat(helper.last()).contains("You can't use the flag");
        this.run(helper, "punish Steve misconduct -I");
        Assertions.assertThat(helper.last()).contains("You can't use the flag");

        final FakeCommandActor banner = FakeCommandActor.player(UUID.randomUUID(), "Banner",
                Permissions.punish(PunishmentType.BAN));
        this.run(banner, "punish Steve misconduct");
        Assertions.assertThat(banner.last()).contains("You don't have permission");
        Assertions.assertThat(this.all()).hasSize(1);
    }

    @Test
    void punishWithTheIpFlagNamesThePlayerAndAddress() {
        this.platform.join(STEVE, "Steve", this.home);
        this.run(this.console, "punish Steve misconduct -I");

        Assertions.assertThat(this.all()).singleElement().satisfies(warning -> Assertions
                .assertThat(warning.victim()).isEqualTo(CompositeVictim.of(STEVE, this.home)));
    }

    @Test
    void templateFlagsTakePresetsOfTheirCommandsType() {
        this.run(this.console, "ban Steve -t misconduct");
        Assertions.assertThat(this.console.last())
                .contains("misconduct is a ladder", "/punish Steve misconduct");
        this.run(this.console, "ban Steve -t spam");
        Assertions.assertThat(this.console.last())
                .contains("spam is a mute template", "use it with /mute");
        this.run(this.console, "ban Steve -t hacking -d 1h");
        Assertions.assertThat(this.console.last())
                .contains("hacking has its own duration", "/ban Steve -t hacking");
        this.run(this.console, "tempban Steve 1h -t hacking");
        Assertions.assertThat(this.console.last()).contains("hacking has its own duration");
        Assertions.assertThat(this.all()).isEmpty();

        this.run(this.console, "tempmute Steve 2h -t spam");
        Assertions.assertThat(this.console.last()).contains("Muted Steve");
        this.run(this.console, "ban Steve -t hacking");
        Assertions.assertThat(this.all()).extracting(Punishment::type)
                .containsExactlyInAnyOrder(PunishmentType.MUTE, PunishmentType.BAN);
    }

    @Test
    void resetsForgetUsesOnlyAfterConfirmation() {
        this.run(this.console, "punish Steve misconduct");
        this.run(this.console, "punish Steve misconduct");
        this.run(this.console, "punish Steve secret");
        Assertions.assertThat(this.templates.nextStep("misconduct",
                PlayerVictim.of(STEVE)).join()).hasValue(2);

        this.run(this.console, "sakurabans templates reset Steve misconduct");
        Assertions.assertThat(this.console.last())
                .contains("forgets 2 uses of misconduct on 1 victims", "--confirm");
        Assertions.assertThat(this.templates.nextStep("misconduct",
                PlayerVictim.of(STEVE)).join()).hasValue(2);
        this.run(this.console, "sakurabans templates reset Steve misconduct --confirm");
        Assertions.assertThat(this.console.last())
                .contains("Forgot 2 uses of misconduct on 1 victims for Steve");
        Assertions.assertThat(this.templates.nextStep("misconduct",
                PlayerVictim.of(STEVE)).join()).hasValue(0);
        // Punishments stay; only the progress goes.
        Assertions.assertThat(this.all()).hasSize(2);

        this.run(this.console, "sakurabans templates reset Steve");
        this.run(this.console, "sakurabans templates reset Steve --confirm");
        Assertions.assertThat(this.console.last())
                .contains("Forgot 1 uses of every template on 1 victims for Steve");
        Assertions.assertThat(new AuditRepository(this.storage).about(SubjectType.PLAYER,
                        STEVE.toString(), 10).join())
                .extracting(entry -> entry.action())
                .as("newest first; the action-only step is audited too")
                .containsExactly(AuditAction.RESET, AuditAction.RESET, AuditAction.APPLY);

        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod", Permissions.PUNISH);
        this.run(mod, "sakurabans templates reset Steve --confirm");
        Assertions.assertThat(mod.last()).contains("You don't have permission");
    }
}
