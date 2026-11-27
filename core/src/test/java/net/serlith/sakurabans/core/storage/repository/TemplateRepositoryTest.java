package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SakuraLadderTemplate;
import net.serlith.sakurabans.core.model.SakuraPresetTemplate;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.model.TemplateSummaries;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Templates in the database and the ladder history (S31); the integration test runs it too. */
class TemplateRepositoryTest extends DatabaseTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    private TemplateRepository templates;
    private PunishmentRepository punishments;
    private InetAddress shared;

    @BeforeEach
    void fixture() throws Exception {
        this.templates = new TemplateRepository(this.storage, this.hasher, AltPolicy.NONE);
        this.punishments = new PunishmentRepository(this.storage, this.hasher,
                new PunishmentMapper(Long::toString));
        this.shared = InetAddress.getByName("203.0.113.2");
        final PlayerRepository players = new PlayerRepository(this.storage, this.hasher);
        players.recordConnection(STEVE, "Steve", this.now.get(), this.shared, null).join();
        players.recordConnection(ALEX, "Alex", this.now.get(), this.shared, null).join();
    }

    private static TemplateInfo hacking() {
        return TemplateBuilder.ladder("hacking")
                .ip(true)
                .scope(ServerScope.of("survival"))
                .reason("Hacking")
                .steps(List.of(TemplateStep.of(PunishmentType.BAN,
                                TemplateDuration.fixed(Duration.ofDays(1))),
                        TemplateStep.none(List.of("say {victim} is gone")),
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open(),
                                "Hacking again", null, List.of()),
                        TemplateStep.of(PunishmentType.MUTE, TemplateDuration.permanent(), null,
                                "<red>Muted</red>", List.of())))
                .expiry(Duration.ofDays(30))
                .actions(List.of("say once", "say twice"))
                .groups(Map.of("cheating", 2, "all", 1))
                .build();
    }

    private long punish(final Victim victim, final String template) {
        final InsertOutcome outcome = this.punishments.insert(new NewPunishment(
                PunishmentType.WARN, false, victim, GlobalScope.INSTANCE, "reason", CONSOLE,
                this.now.get(), null, null, Visibility.NORMAL, template, 0, List.of(), null),
                OverridePolicy.REJECT, new NewTemplateUse(template, 0, victim, CONSOLE,
                        this.now.get())).join();
        return ((InsertOutcome.Inserted) outcome).punishment().id();
    }

    private Map<String, Long> history(final Victim victim, final String... templates) {
        return this.templates.history(Set.of(templates), victim, false, null,
                this.now.get()).join();
    }

    @Test
    void templatesRoundTripWithStepsGroupsAndActions() {
        final TemplateRepository.Write created = this.templates.create(hacking(), CONSOLE,
                this.now.get()).join();

        Assertions.assertThat(created).isInstanceOf(TemplateRepository.Write.Done.class);
        Assertions.assertThat(this.templates.all().join()).singleElement().satisfies(stored -> {
            Assertions.assertThat(TemplateSummaries.summary(stored.template()))
                    .isEqualTo(TemplateSummaries.summary(
                            ((TemplateRepository.Write.Done) created).value().template()));
            final SakuraLadderTemplate ladder = (SakuraLadderTemplate) stored.template();
            Assertions.assertThat(ladder.steps().get(1).actions())
                    .containsExactly("say {victim} is gone");
            Assertions.assertThat(ladder.steps().get(1).type()).isEmpty();
            Assertions.assertThat(ladder.steps().get(2).duration())
                    .isEqualTo(TemplateDuration.open());
            Assertions.assertThat(ladder.steps().get(2).reason()).contains("Hacking again");
            Assertions.assertThat(ladder.steps().get(3).duration())
                    .isEqualTo(TemplateDuration.permanent());
            Assertions.assertThat(ladder.expiry()).contains(Duration.ofDays(30));
            Assertions.assertThat(stored.template().groups()).containsEntry("cheating", 2);
            Assertions.assertThat(stored.version()).isEqualTo(1);
            Assertions.assertThat(stored.template().ip()).isTrue();
            Assertions.assertThat(stored.template().scope())
                    .contains(ServerScope.of("survival"));
        });
        Assertions.assertThat(this.templates.create(hacking(), CONSOLE,
                this.now.get()).join()).isInstanceOf(TemplateRepository.Write.Taken.class);
    }

    @Test
    void updatesCheckVersionsAndSkipNoChanges() {
        this.templates.create(hacking(), CONSOLE, this.now.get()).join();
        final TemplateInfo changed = TemplateBuilder.preset("hacking", PunishmentType.BAN)
                .reason("Cheating").duration(TemplateDuration.fixed(Duration.ofDays(3))).build();

        Assertions.assertThat(this.templates.update(hacking(), 1L, CONSOLE,
                this.now.get()).join()).isInstanceOf(TemplateRepository.Write.Unchanged.class);
        Assertions.assertThat(this.templates.update(changed, 3L, CONSOLE,
                this.now.get()).join()).isEqualTo(new TemplateRepository.Write.Conflict(1));
        final TemplateRepository.Write done = this.templates.update(changed, 1L, CONSOLE,
                this.now.get()).join();

        Assertions.assertThat(done).isInstanceOfSatisfying(TemplateRepository.Write.Done.class,
                write -> {
                    Assertions.assertThat(write.value().version()).isEqualTo(2);
                    Assertions.assertThat(write.value().template().groups()).isEmpty();
                    Assertions.assertThat(write.value().template()).isInstanceOfSatisfying(
                            SakuraPresetTemplate.class, preset -> Assertions.assertThat(
                                    preset.duration().fixedLength())
                                    .contains(Duration.ofDays(3)));
                });
        Assertions.assertThat(new AuditRepository(this.storage).about(SubjectType.TEMPLATE,
                        "hacking", 10).join())
                .extracting(entry -> entry.action())
                .containsExactly(AuditAction.UPDATE, AuditAction.CREATE);
    }

    @Test
    void deletingKeepsTheReferenceOnPunishments() {
        this.templates.create(hacking(), CONSOLE, this.now.get()).join();
        this.punish(PlayerVictim.of(STEVE), "hacking");

        Assertions.assertThat(this.templates.delete(null, "hacking", 1, CONSOLE, this.now.get())
                .join()).isInstanceOf(TemplateRepository.Write.Deleted.class);

        Assertions.assertThat(this.templates.all().join()).isEmpty();
        Assertions.assertThat(this.punishments.byId(1).join().orElseThrow().template())
                .hasValueSatisfying(ref -> Assertions.assertThat(ref.name()).isEqualTo("hacking"));
    }

    @Test
    void historyCountsTheVictimOrItsAltsWhenShared() {
        this.punish(PlayerVictim.of(STEVE), "hacking");
        this.punish(PlayerVictim.of(STEVE), "xray");
        this.punish(PlayerVictim.of(ALEX), "hacking");
        this.punish(AddressVictim.of(this.shared), "hacking");

        Assertions.assertThat(this.templates.history(Set.of("hacking", "xray"),
                        PlayerVictim.of(STEVE), false, null, this.now.get()).join())
                .containsExactlyInAnyOrderEntriesOf(Map.of("hacking", 1L, "xray", 1L));
        Assertions.assertThat(this.templates.history(Set.of("hacking"), PlayerVictim.of(STEVE),
                true, null, this.now.get()).join()).containsEntry("hacking", 3L);
        Assertions.assertThat(this.templates.history(Set.of("hacking"),
                        AddressVictim.of(this.shared), false, null, this.now.get()).join())
                .containsEntry("hacking", 1L);
        Assertions.assertThat(this.templates.history(Set.of("hacking"), PlayerVictim.of(STEVE),
                        false, this.now.get().plusSeconds(1), this.now.get()).join())
                .isEmpty();
    }

    private NewPunishment warning(final Victim victim, final String template, final int step) {
        return new NewPunishment(PunishmentType.WARN, false, victim, GlobalScope.INSTANCE,
                "reason", CONSOLE, this.now.get(), null, null, Visibility.NORMAL, template, step,
                List.of(), null);
    }

    @Test
    void useExpectingStepIsStoredOnlyWhileTheCountHolds() {
        // S64: the storing transaction locks the victim and counts again.
        final LadderCount count = new LadderCount(Map.of("ladder", 1), false, null,
                this.now.get(), points -> (int) Math.min(points, 3));
        final PlayerVictim steve = PlayerVictim.of(STEVE);
        Assertions.assertThat(this.templates.step(steve, count).join()).isZero();
        final NewTemplateUse first = new NewTemplateUse("ladder", 0, steve, CONSOLE,
                this.now.get());
        Assertions.assertThat(this.templates.recordUse(first, new ExpectedStep(count, 0))
                .join()).isInstanceOf(TemplateRepository.UseOutcome.Recorded.class);

        // Expecting step 0 again: the count is 1 now, nothing is stored.
        Assertions.assertThat(this.templates.recordUse(first, new ExpectedStep(count, 0))
                .join()).isEqualTo(new TemplateRepository.UseOutcome.StepChanged(1));
        Assertions.assertThat(this.punishments.insert(this.warning(steve, "ladder", 0),
                OverridePolicy.REJECT, first, new ExpectedStep(count, 0)).join())
                .isEqualTo(new InsertOutcome.StepChanged(1));
        final int stored = this.read(dsl -> dsl.fetchCount(SakuraTables.PUNISHMENTS));
        Assertions.assertThat(stored).isZero();
        Assertions.assertThat(this.history(steve, "ladder")).containsEntry("ladder", 1L);

        Assertions.assertThat(this.punishments.insert(this.warning(steve, "ladder", 1),
                OverridePolicy.REJECT, new NewTemplateUse("ladder", 1, steve, CONSOLE,
                        this.now.get()), new ExpectedStep(count, 1)).join())
                .isInstanceOf(InsertOutcome.Inserted.class);
        Assertions.assertThat(this.templates.step(steve, count).join()).isEqualTo(2);
        // A victim without a row yet: the lock creates it.
        Assertions.assertThat(this.templates.recordUse(new NewTemplateUse("ladder", 0,
                PlayerVictim.of(ALEX), CONSOLE, this.now.get()), new ExpectedStep(count, 0))
                .join()).isInstanceOf(TemplateRepository.UseOutcome.Recorded.class);
    }

    @Test
    void usesWithoutPunishmentCountButVoidedOrExpungedOnesDont() {
        final long kept = this.punish(PlayerVictim.of(STEVE), "ladder");
        this.templates.recordUse(new NewTemplateUse("ladder", 1, PlayerVictim.of(STEVE),
                CONSOLE, this.now.get()), null).join();
        final long voided = this.punish(PlayerVictim.of(STEVE), "ladder");
        final long expunged = this.punish(PlayerVictim.of(STEVE), "ladder");
        Assertions.assertThat(this.history(PlayerVictim.of(STEVE), "ladder"))
                .containsEntry("ladder", 4L);

        this.punishments.voidPunishment(voided, CONSOLE, null, this.now.get()).join();
        this.punishments.expunge(expunged, CONSOLE, this.now.get()).join();
        Assertions.assertThat(this.history(PlayerVictim.of(STEVE), "ladder"))
                .containsEntry("ladder", 2L);
        // A revoked one still counts.
        this.punishments.end(kept, EndKind.REVOKED, this.now.get(), CONSOLE, null).join();
        Assertions.assertThat(this.history(PlayerVictim.of(STEVE), "ladder"))
                .containsEntry("ladder", 2L);
        Assertions.assertThat(this.storage.read(dsl -> dsl.fetchCount(
                SakuraTables.TEMPLATE_USES)).join()).isEqualTo(3);
    }

    @Test
    void usesCountForWhomTheTemplateWasAppliedTo() {
        // An ip template punishes the address, but the use is the player's.
        final InsertOutcome outcome = this.punishments.insert(new NewPunishment(
                PunishmentType.BAN, false, AddressVictim.of(this.shared), GlobalScope.INSTANCE,
                "reason", CONSOLE, this.now.get(), null, null, Visibility.NORMAL, "ip-ban", null,
                List.of(), null), OverridePolicy.REJECT, new NewTemplateUse("ip-ban", null,
                        PlayerVictim.of(STEVE), CONSOLE, this.now.get())).join();
        Assertions.assertThat(outcome).isInstanceOf(InsertOutcome.Inserted.class);

        Assertions.assertThat(this.history(PlayerVictim.of(STEVE), "ip-ban"))
                .containsEntry("ip-ban", 1L);
        Assertions.assertThat(this.history(AddressVictim.of(this.shared), "ip-ban")).isEmpty();
        // A punishment rejected by the override policy records no use.
        this.punishments.insert(new NewPunishment(PunishmentType.BAN, false,
                AddressVictim.of(this.shared), GlobalScope.INSTANCE, "reason", CONSOLE,
                this.now.get(), null, null, Visibility.NORMAL, "ip-ban", null, List.of(), null),
                OverridePolicy.REJECT, new NewTemplateUse("ip-ban", null,
                        PlayerVictim.of(STEVE), CONSOLE, this.now.get())).join();
        Assertions.assertThat(this.history(PlayerVictim.of(STEVE), "ip-ban"))
                .containsEntry("ip-ban", 1L);
    }
}
