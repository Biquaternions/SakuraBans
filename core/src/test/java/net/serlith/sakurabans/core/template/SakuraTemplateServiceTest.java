package net.serlith.sakurabans.core.template;

import java.net.InetAddress;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.event.TemplateAppliedEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.SakuraLadderTemplate;
import net.serlith.sakurabans.core.model.SakuraPresetTemplate;
import net.serlith.sakurabans.core.model.StoredTemplate;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.model.TemplateKind;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SakuraTemplateServiceTest extends ServiceTest {

    private static List<TemplateStep> bans(final Duration... steps) {
        return Arrays.stream(steps).map(step -> TemplateStep.of(PunishmentType.BAN,
                TemplateDuration.ofNullable(step))).toList();
    }

    private static TemplateBuilder template(final String name,
            final PunishmentType type) {
        return TemplateBuilder.preset(name, type).reason("Reason");
    }

    private static TemplateBuilder ladder(final String name,
            final Duration... steps) {
        return TemplateBuilder.ladder(name).reason("Reason").steps(bans(steps));
    }

    private int step(final String template, final UUID victim) {
        return this.templates.nextStep(template, PlayerVictim.of(victim)).join().orElseThrow();
    }

    private LadderResult apply(final UUID victim, final String ladder) {
        return this.service.applyLadder(LadderRequest.builder(ladder, PlayerVictim.of(victim),
                MODERATOR).build(), PunishOptions.defaults()).join();
    }

    private Punishment applyAndLift(final UUID victim, final String ladder) {
        final Punishment ban = ((LadderResult.Punished) this.apply(victim, ladder)).punishment();
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(ban.id()),
                MODERATOR).build()).join();
        return ban;
    }

    private StoredTemplate create(final TemplateInfo definition) {
        return ((AdminResult.Ok<StoredTemplate>) this.templates.create(definition, CONSOLE)
                .join()).value();
    }

    private PunishResult punish(final UUID victim, final String template) {
        return this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(victim), MODERATOR).template(template).build(),
                PunishOptions.defaults()).join();
    }

    private Punishment banAndLift(final UUID victim, final String template) {
        final Punishment ban = ((PunishResult.Success) this.punish(victim, template)).punishment();
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(ban.id()),
                MODERATOR).build()).join();
        return ban;
    }

    private static Duration length(final Punishment punishment) {
        return punishment.expiresAt()
                .map(expiry -> Duration.between(punishment.createdAt(), expiry))
                .orElse(null);
    }

    @Test
    void eachPunishmentClimbsTheLadderAndTheLastStepRepeats() {
        this.create(ladder("hacking", Duration.ofDays(1), Duration.ofDays(7), null)
                .reason("Hacking").build());

        final Punishment first = this.applyAndLift(STEVE, "hacking");
        final Punishment second = this.applyAndLift(STEVE, "hacking");
        final Punishment third = this.applyAndLift(STEVE, "hacking");
        final Punishment fourth = this.applyAndLift(STEVE, "hacking");

        Assertions.assertThat(length(first)).isEqualTo(Duration.ofDays(1));
        Assertions.assertThat(length(second)).isEqualTo(Duration.ofDays(7));
        Assertions.assertThat(third.permanent()).isTrue();
        Assertions.assertThat(fourth.permanent()).isTrue();
        Assertions.assertThat(first.reason()).isEqualTo("Hacking");
        Assertions.assertThat(second.template()).hasValueSatisfying(ref -> {
            Assertions.assertThat(ref.name()).isEqualTo("hacking");
            Assertions.assertThat(ref.step()).hasValue(1);
        });
        // Alex has his own ladder.
        Assertions.assertThat(length(this.applyAndLift(ALEX, "hacking")))
                .isEqualTo(Duration.ofDays(1));
        Assertions.assertThat(this.events(TemplateAppliedEvent.class)).hasSize(5);
    }

    @Test
    void ladderExpiryForgetsOldPunishments() {
        this.create(ladder("spam", Duration.ofHours(1), Duration.ofDays(1))
                .expiry(Duration.ofDays(30)).build());
        this.applyAndLift(STEVE, "spam");

        Assertions.assertThat(this.step("spam", STEVE)).isEqualTo(1);
        this.now.set(this.now.get().plus(Duration.ofDays(31)));
        Assertions.assertThat(this.step("spam", STEVE)).isZero();
        Assertions.assertThat(this.templates.nextStep("nothing", PlayerVictim.of(STEVE)).join())
                .isEmpty();
    }

    @Test
    void groupMatesCountWithTheirWeight() {
        final Duration[] five = {Duration.ofHours(1), Duration.ofHours(2),
                Duration.ofHours(3), Duration.ofHours(4), Duration.ofHours(5),
                Duration.ofHours(6)};
        this.create(ladder("hacking", five).groups(Map.of("cheating", 2)).build());
        this.create(ladder("xray", five).groups(Map.of("cheating", 1)).build());
        this.create(ladder("spam", five).build());
        // A preset in the group counts towards its ladders too.
        this.create(template("fly", PunishmentType.BAN).groups(Map.of("cheating", 1)).build());

        this.applyAndLift(STEVE, "hacking");
        this.applyAndLift(STEVE, "spam");

        // hacking (worth 2) counts on the xray ladder; spam shares no group.
        Assertions.assertThat(this.step("xray", STEVE)).isEqualTo(2);
        this.applyAndLift(STEVE, "xray");
        Assertions.assertThat(this.step("hacking", STEVE)).isEqualTo(3);
        Assertions.assertThat(this.step("spam", STEVE)).isEqualTo(1);
        this.banAndLift(STEVE, "fly");
        Assertions.assertThat(this.step("hacking", STEVE)).isEqualTo(4);
        Assertions.assertThat(this.templates.nextStep("fly", PlayerVictim.of(STEVE)).join())
                .as("a preset has no step").isEmpty();
    }

    @Test
    void sharedProgressFollowsAddresses() throws Exception {
        final InetAddress shared = InetAddress.getByName("203.0.113.9");
        this.players.recordConnection(STEVE, "Steve", this.now.get(), shared, null).join();
        this.players.recordConnection(ALEX, "Alex", this.now.get(), shared, null).join();
        this.create(ladder("alting", Duration.ofDays(1), Duration.ofDays(2))
                .addressShared(true).build());
        this.create(ladder("solo", Duration.ofDays(1), Duration.ofDays(2)).build());

        this.applyAndLift(STEVE, "alting");
        this.applyAndLift(STEVE, "solo");

        Assertions.assertThat(length(this.applyAndLift(ALEX, "alting")))
                .isEqualTo(Duration.ofDays(2));
        Assertions.assertThat(length(this.applyAndLift(ALEX, "solo")))
                .isEqualTo(Duration.ofDays(1));
    }

    @Test
    void actionsRunWithPlaceholders() {
        this.names.put(STEVE, "Steve");
        this.create(TemplateBuilder.ladder("hacking").reason("Hacking")
                .steps(List.of(TemplateStep.of(PunishmentType.BAN,
                        TemplateDuration.fixed(Duration.ofDays(1)), null, null,
                        List.of("/say step {step}: {duration}"))))
                .actions(List.of("note {victim} {uuid} {template} #{id} by {operator}: {reason}"))
                .build());
        this.create(template("fly", PunishmentType.BAN)
                .duration(TemplateDuration.permanent())
                .actions(List.of("say {template} step '{step}' {duration}")).build());

        final Punishment ban = ((LadderResult.Punished) this.apply(STEVE, "hacking"))
                .punishment();
        this.punish(ALEX, "fly");

        Assertions.assertThat(this.platform.consoleCommands()).containsExactly(
                "note Steve " + STEVE + " hacking #" + ban.id() + " by Mod: Hacking",
                "say step 1: 1d", "say fly step '' permanent");
    }

    @Test
    void draftsTakePresetsOfTheirTypeAndLaddersTakeLadders() {
        this.create(template("spam", PunishmentType.MUTE).build());
        this.create(ladder("hacking", Duration.ofDays(1)).build());

        Assertions.assertThat(List.of(this.punish(STEVE, "spam"), this.punish(STEVE, "hacking"),
                        this.punish(STEVE, "nothing")))
                .extracting(result -> ((PunishResult.Failure) result).reason())
                .containsExactly(PunishResult.Failure.Reason.TEMPLATE_TYPE_MISMATCH,
                        PunishResult.Failure.Reason.TEMPLATE_TYPE_MISMATCH,
                        PunishResult.Failure.Reason.UNKNOWN_TEMPLATE);
        Assertions.assertThat(List.of(this.apply(STEVE, "spam"), this.apply(STEVE, "nothing")))
                .extracting(result -> ((LadderResult.Failure) result).reason())
                .containsExactly(PunishResult.Failure.Reason.TEMPLATE_TYPE_MISMATCH,
                        PunishResult.Failure.Reason.UNKNOWN_TEMPLATE);
        Assertions.assertThat(this.service.query(PunishmentQuery.builder().build()).join()
                .items()).isEmpty();
    }

    @Test
    void presetDurationsAreTheCommandsOnlyWhenOpen() {
        this.create(template("open", PunishmentType.BAN).build());
        this.create(template("fixed", PunishmentType.BAN)
                .duration(TemplateDuration.fixed(Duration.ofDays(3))).build());
        this.create(template("forever", PunishmentType.BAN)
                .duration(TemplateDuration.permanent()).build());
        this.create(template("caution", PunishmentType.WARN).build());

        final Punishment open = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).template("open")
                .duration(Duration.ofHours(2)).build(), PunishOptions.defaults()).join())
                .punishment();
        Assertions.assertThat(length(open)).isEqualTo(Duration.ofHours(2));
        Assertions.assertThat(length(this.banAndLift(ALEX, "fixed")))
                .isEqualTo(Duration.ofDays(3));
        Assertions.assertThat(this.banAndLift(ALEX, "forever").permanent()).isTrue();
        final Punishment warning = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.WARN, PlayerVictim.of(ALEX), MODERATOR)
                .template("caution").build(), PunishOptions.defaults()).join()).punishment();
        Assertions.assertThat(length(warning)).as("warnings.expire-after")
                .isEqualTo(this.config.settings().warnings().expireAfter());
        this.create(template("rename", PunishmentType.KICK).reason("Change your name").build());
        final Punishment kick = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.KICK, PlayerVictim.of(ALEX), MODERATOR)
                .template("rename").build(), PunishOptions.defaults()).join()).punishment();
        Assertions.assertThat(kick.reason()).isEqualTo("Change your name");
        Assertions.assertThat(kick.template()).hasValueSatisfying(ref -> Assertions
                .assertThat(ref.step()).as("a preset has no step").isEmpty());
        for (final String fixed : List.of("fixed", "forever")) {
            Assertions.assertThatThrownBy(() -> this.service.punish(DraftPunishment.builder(
                    PunishmentType.BAN, PlayerVictim.of(ALEX), MODERATOR).template(fixed)
                    .duration(Duration.ofHours(1)).build(), PunishOptions.defaults()).join())
                    .as(fixed).hasCauseInstanceOf(ValidationException.class);
        }
    }

    @Test
    void editingIsValidatedVersionedAndSynced() {
        final StoredTemplate created = this.create(ladder("spam", Duration.ofHours(1),
                Duration.ofDays(1)).build());
        this.create(template("flood", PunishmentType.MUTE).build());

        Assertions.assertThat(this.templates.create(template("spam", PunishmentType.MUTE)
                .build(), CONSOLE).join()).as("names are unique across kinds")
                .isInstanceOf(AdminResult.ValidationFailed.class);
        // Built without the builder's checks: the service checks again.
        final TemplateInfo loud = template("loud", PunishmentType.MUTE).shadow(true).build();
        Assertions.assertThat(this.templates.create(new SakuraPresetTemplate(loud.name(),
                PunishmentType.BAN, TemplateDuration.open(), false, true, null, loud.reason(),
                false, List.of(), Map.of(), null, TemplateDisplay.CHAT, null),
                CONSOLE).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThatThrownBy(() -> template("k", PunishmentType.KICK)
                        .duration(TemplateDuration.fixed(Duration.ofDays(1))).build())
                .isInstanceOf(ValidationException.class);
        Assertions.assertThat(this.templates.reorderSteps(null, "flood", List.of(0), 1, CONSOLE)
                .join()).as("a preset has no steps")
                .isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.templates.reorderSteps(null, "spam", List.of(0, 0), 1, CONSOLE)
                .join()).isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.templates.reorderSteps(null, "spam", List.of(1, 0), 7, CONSOLE)
                .join()).isInstanceOf(AdminResult.Conflict.class);

        final AdminResult<StoredTemplate> reordered =
                this.templates.reorderSteps(null, "spam", List.of(1, 0), created.version(), CONSOLE)
                        .join();
        Assertions.assertThat(reordered).isInstanceOfSatisfying(AdminResult.Ok.class, ok -> {
            final StoredTemplate value = (StoredTemplate) ok.value();
            Assertions.assertThat(((SakuraLadderTemplate) value.template()).steps().getFirst()
                    .duration().fixedLength())
                    .hasValue(Duration.ofDays(1));
            Assertions.assertThat(value.version()).isEqualTo(2);
        });
        Assertions.assertThat(this.templates.find("SPAM").orElseThrow().version())
                .isEqualTo(2);
        Assertions.assertThat(this.templates.delete(null, "spam", 2, CONSOLE).join())
                .isInstanceOf(AdminResult.Ok.class);
        Assertions.assertThat(this.templates.templates()).extracting(TemplateInfo::name)
                .containsExactly("flood");
        Assertions.assertThat(this.templates.delete(null, "spam", 2, CONSOLE).join())
                .isInstanceOf(AdminResult.NotFound.class);

        Assertions.assertThat(this.published).filteredOn(SyncPacket.EntityChangedPacket.class
                ::isInstance).containsOnly(new SyncPacket.EntityChangedPacket(
                        SubjectType.TEMPLATE)).hasSize(4);
    }

    @Test
    void forbiddenActorsChangeNothing() {
        // The moderator without the template permission.
        this.platform.join(MOD, "Mod", this.home);

        Assertions.assertThat(this.templates.create(template("spam", PunishmentType.MUTE)
                .build(), MODERATOR).join()).isInstanceOf(AdminResult.Forbidden.class);
        Assertions.assertThat(this.templates.templates()).isEmpty();
    }

    @Test
    void otherInstancesReloadThroughTheSync() {
        // Written "elsewhere": stored, but not in this instance's memory yet.
        this.templateRepository.create(template("spam", PunishmentType.MUTE).build(), CONSOLE,
                this.now.get()).join();
        Assertions.assertThat(this.templates.template("spam")).isEmpty();

        this.syncReceiver().handle(new SyncPacket.EntityChangedPacket(SubjectType.TEMPLATE))
                .join();

        Assertions.assertThat(this.templates.template("spam")).isPresent();
    }

    @Test
    void theShippedFileIsImportedOnceAndMergedOnRequest() throws Exception {
        this.templates.start().join();
        Assertions.assertThat(this.templates.templates()).extracting(TemplateInfo::name)
                .contains("hacking", "spam", "xray");
        final StoredTemplate spam = this.templates.find("spam").orElseThrow();
        this.templates.delete(null, "spam", spam.version(), CONSOLE).join();

        this.templates.start().join();
        Assertions.assertThat(this.templates.template("spam")).isEmpty();

        Files.writeString(this.dir.resolve("templates.yml"),
                """
                templates:
                  spam:
                    type: mute
                    reason: Reason
                    duration: 1h
                  hacking:
                    type: ladder
                    reason: Changed
                    steps: [ban permanent]
                """);
        final SakuraTemplateService.MergeReport report = this.templates.merge(
                new TemplateFiles(this.dir).read()
                        .templates(), CONSOLE).join();

        Assertions.assertThat(report.created()).isEqualTo(1);
        Assertions.assertThat(report.updated()).isEqualTo(1);
        Assertions.assertThat(this.templates.template("xray")).isPresent();
        Assertions.assertThat(this.templates.template("hacking").orElseThrow().reason())
                .isEqualTo("Changed");
    }
}
