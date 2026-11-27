package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.Map;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /sb templates reset <player|address> [template]} deletes what each ladder counts
 * for the target, alts and addresses included for an {@code address-shared} ladder.
 */
class TemplateResetTest extends CommandTest {

    @BeforeEach
    void templates() {
        // Steve and Alex share an address.
        this.known(STEVE, "Steve");
        this.known(ALEX, "Alex");
        final List<TemplateStep> warnings = List.of(
                TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()));
        this.templates.create(TemplateBuilder.ladder("chat").reason("Chat").steps(warnings)
                .addressShared(true).groups(Map.of("talk", 1)).build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("caps", PunishmentType.WARN)
                .reason("Caps").groups(Map.of("talk", 1)).build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.ladder("grief").reason("Grief").steps(warnings)
                .build(), CONSOLE).join();
    }

    private void apply(final String ladder, final Victim victim) {
        this.service.applyLadder(LadderRequest.builder(ladder, victim, CONSOLE).build(),
                PunishOptions.defaults()).join();
    }

    private int next(final String ladder, final Victim victim) {
        return this.templates.nextStep(ladder, victim).join().orElseThrow();
    }

    private int uses() {
        return this.read(dsl -> dsl.fetchCount(SakuraTables.TEMPLATE_USES));
    }

    private List<AuditEntry> resets(final SubjectType type, final String subject) {
        return new AuditRepository(this.storage).about(type, subject, 10).join().stream()
                .filter(entry -> entry.action() == AuditAction.RESET).toList();
    }

    @Test
    void anAddressSharedLadderStartsOverAcrossAltsAndAddresses() {
        final PlayerVictim steve = PlayerVictim.of(STEVE);
        final PlayerVictim alex = PlayerVictim.of(ALEX);
        this.apply("chat", steve);
        this.apply("chat", steve);
        this.apply("chat", alex);
        this.apply("chat", AddressVictim.of(this.home));
        this.apply("grief", alex);
        Assertions.assertThat(this.next("chat", steve)).isEqualTo(4);

        this.run(this.console, "sakurabans templates reset Steve chat");
        Assertions.assertThat(this.console.last())
                .contains("forgets 4 uses of chat on 3 victims", "--confirm");
        Assertions.assertThat(this.uses()).isEqualTo(5);
        this.run(this.console, "sakurabans templates reset Steve chat --confirm");
        Assertions.assertThat(this.console.last())
                .contains("Forgot 4 uses of chat on 3 victims for Steve");

        Assertions.assertThat(this.next("chat", steve)).isZero();
        Assertions.assertThat(this.next("chat", alex)).isZero();
        Assertions.assertThat(this.next("grief", alex)).as("not shared, not named").isOne();
        Assertions.assertThat(this.resets(SubjectType.PLAYER, STEVE.toString()))
                .singleElement().satisfies(entry -> Assertions.assertThat(entry.oldValue())
                        .isEqualTo("template uses of chat: 4 of 3 victims"));
    }

    @Test
    void groupMateCountedByTheLadderGoesWithIt() {
        final PlayerVictim alex = PlayerVictim.of(ALEX);
        this.apply("chat", alex);
        this.service.punish(DraftPunishment.builder(
                PunishmentType.WARN, PlayerVictim.of(STEVE), CONSOLE).template("caps").build(),
                PunishOptions.defaults()).join();
        Assertions.assertThat(this.next("chat", alex)).isEqualTo(2);

        this.run(this.console, "sakurabans templates reset Alex chat");
        this.run(this.console, "sakurabans templates reset Alex chat --confirm");
        Assertions.assertThat(this.console.last()).contains("Forgot 2 uses of chat on 2 victims");
        Assertions.assertThat(this.next("chat", alex)).isZero();

        // A preset alone: only the target's own uses of it.
        this.service.punish(DraftPunishment.builder(
                PunishmentType.WARN, PlayerVictim.of(STEVE), CONSOLE).template("caps").build(),
                PunishOptions.defaults()).join();
        this.run(this.console, "sakurabans templates reset Alex caps");
        Assertions.assertThat(this.console.last()).contains("forgets 0 uses of caps");
    }

    @Test
    void anAddressCanBeResetAndIsAuditedByItsHash() {
        final AddressVictim address = AddressVictim.of(this.home);
        this.apply("grief", address);
        this.apply("grief", address);
        this.apply("grief", PlayerVictim.of(STEVE));
        this.apply("chat", PlayerVictim.of(ALEX));
        final String text = this.home.getHostAddress();

        this.run(this.console, "sakurabans templates reset " + text);
        // Its own uses of every template, and what the shared ladder counts for it.
        Assertions.assertThat(this.console.last())
                .contains("forgets 3 uses of every template on 2 victims");
        this.run(this.console, "sakurabans templates reset " + text + " --confirm");
        Assertions.assertThat(this.console.last())
                .contains("Forgot 3 uses of every template on 2 victims for " + text);

        Assertions.assertThat(this.next("grief", address)).isZero();
        Assertions.assertThat(this.next("grief", PlayerVictim.of(STEVE))).as("his own")
                .isOne();
        Assertions.assertThat(this.next("chat", PlayerVictim.of(ALEX))).isZero();
        final String hash = this.hasher.hex(this.home);
        Assertions.assertThat(this.resets(SubjectType.ADDRESS_HASH, hash)).singleElement()
                .satisfies(entry -> Assertions.assertThat(entry.oldValue())
                        .isEqualTo("template uses of every template: 3 of 2 victims"));
        final int literal = this.read(dsl -> dsl.fetchCount(SakuraTables.AUDIT_LOG,
                SakuraTables.AUDIT_LOG.SUBJECT_ID.eq(text)
                        .or(SakuraTables.AUDIT_LOG.OLD_VALUE.like("%" + text + "%"))));
        Assertions.assertThat(literal).isZero();
    }

    @Test
    void rangesAndIdsAreNotTargets() {
        this.run(this.console, "sakurabans templates reset 203.0.113.0/24");
        Assertions.assertThat(this.console.last())
                .contains("Name a player or an address to reset, not 203.0.113.0/24");
        this.run(this.console, "sakurabans templates reset #1");
        Assertions.assertThat(this.console.last()).contains("not a punishment id");
        this.run(this.console, "sakurabans templates reset Nobody");
        Assertions.assertThat(this.console.last()).contains("Nobody is known as Nobody");
    }
}
