package net.serlith.sakurabans.core.template;

import java.net.InetAddress;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.event.TemplateStepEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.SakuraTemplateInfo;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SakuraTemplateServiceTest extends ServiceTest {

    private static List<TemplateStep> ladder(final Duration... steps) {
        return Arrays.stream(steps).map(TemplateStep::of).toList();
    }

    private static TemplateDefinition.Builder template(final String name,
            final PunishmentType type) {
        return TemplateDefinition.builder(name, type).reason("Reason");
    }

    private int step(final String template, final UUID victim) {
        return this.templates.resolve(template, PlayerVictim.of(victim)).join().orElseThrow()
                .step();
    }

    private SakuraTemplateInfo create(final TemplateDefinition definition) {
        return ((AdminResult.Ok<SakuraTemplateInfo>) this.templates.create(definition, CONSOLE)
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
        this.create(TemplateDefinition.builder("hacking", PunishmentType.BAN).reason("Hacking")
                .ladder(List.of(TemplateStep.of(Duration.ofDays(1)),
                        TemplateStep.of(Duration.ofDays(7)), TemplateStep.of(null)))
                .build());

        final Punishment first = this.banAndLift(STEVE, "hacking");
        final Punishment second = this.banAndLift(STEVE, "hacking");
        final Punishment third = this.banAndLift(STEVE, "hacking");
        final Punishment fourth = this.banAndLift(STEVE, "hacking");

        Assertions.assertThat(length(first)).isEqualTo(Duration.ofDays(1));
        Assertions.assertThat(length(second)).isEqualTo(Duration.ofDays(7));
        Assertions.assertThat(third.permanent()).isTrue();
        Assertions.assertThat(fourth.permanent()).isTrue();
        Assertions.assertThat(first.reason()).isEqualTo("Hacking");
        Assertions.assertThat(second.template()).hasValueSatisfying(ref -> {
            Assertions.assertThat(ref.name()).isEqualTo("hacking");
            Assertions.assertThat(ref.step()).isEqualTo(1);
        });
        // Alex has his own ladder.
        Assertions.assertThat(length(this.banAndLift(ALEX, "hacking")))
                .isEqualTo(Duration.ofDays(1));
        Assertions.assertThat(this.events(TemplateStepEvent.class)).hasSize(5);
    }

    @Test
    void ladderExpiryForgetsOldPunishments() {
        this.create(template("spam", PunishmentType.BAN)
                .ladder(ladder(Duration.ofHours(1), Duration.ofDays(1)))
                .ladderExpiry(Duration.ofDays(30)).build());
        this.banAndLift(STEVE, "spam");

        Assertions.assertThat(this.step("spam", STEVE)).isEqualTo(1);
        this.now.set(this.now.get().plus(Duration.ofDays(31)));
        Assertions.assertThat(this.templates.resolve("spam", PlayerVictim.of(STEVE)).join())
                .hasValueSatisfying(resolution -> {
                    Assertions.assertThat(resolution.step()).isZero();
                    Assertions.assertThat(resolution.duration()).isEqualTo(Duration.ofHours(1));
                });
        Assertions.assertThat(this.templates.resolve("nothing", PlayerVictim.of(STEVE)).join())
                .isEmpty();
    }

    @Test
    void groupMatesCountWithTheirWeight() {
        final List<TemplateStep> five = ladder(Duration.ofHours(1), Duration.ofHours(2),
                Duration.ofHours(3), Duration.ofHours(4), Duration.ofHours(5));
        this.create(template("hacking", PunishmentType.BAN).ladder(five)
                .groups(Map.of("cheating", 2)).build());
        this.create(template("xray", PunishmentType.BAN).ladder(five)
                .groups(Map.of("cheating", 1)).build());
        this.create(template("spam", PunishmentType.BAN).ladder(five).build());

        this.banAndLift(STEVE, "hacking");
        this.banAndLift(STEVE, "spam");

        // hacking (worth 2) counts on the xray ladder; spam shares no group.
        Assertions.assertThat(this.step("xray", STEVE)).isEqualTo(2);
        this.banAndLift(STEVE, "xray");
        Assertions.assertThat(this.step("hacking", STEVE)).isEqualTo(3);
        Assertions.assertThat(this.step("spam", STEVE)).isEqualTo(1);
    }

    @Test
    void sharedProgressFollowsAddresses() throws Exception {
        final InetAddress shared = InetAddress.getByName("203.0.113.9");
        this.players.recordConnection(STEVE, "Steve", this.now.get(), shared, null).join();
        this.players.recordConnection(ALEX, "Alex", this.now.get(), shared, null).join();
        this.create(template("alting", PunishmentType.BAN).addressShared(true)
                .ladder(ladder(Duration.ofDays(1), Duration.ofDays(2))).build());
        this.create(template("solo", PunishmentType.BAN)
                .ladder(ladder(Duration.ofDays(1), Duration.ofDays(2))).build());

        this.banAndLift(STEVE, "alting");
        this.banAndLift(STEVE, "solo");

        Assertions.assertThat(length(this.banAndLift(ALEX, "alting")))
                .isEqualTo(Duration.ofDays(2));
        Assertions.assertThat(length(this.banAndLift(ALEX, "solo")))
                .isEqualTo(Duration.ofDays(1));
    }

    @Test
    void actionsRunWithPlaceholders() {
        this.names.put(STEVE, "Steve");
        this.create(TemplateDefinition.builder("hacking", PunishmentType.BAN).reason("Hacking")
                .ladder(List.of(TemplateStep.of(Duration.ofDays(1),
                        List.of("/say step {step}: {duration}"))))
                .actions(List.of("note {victim} {uuid} {template} #{id} by {operator}: {reason}"))
                .build());

        final Punishment ban = ((PunishResult.Success) this.punish(STEVE, "hacking"))
                .punishment();

        Assertions.assertThat(this.platform.consoleCommands()).containsExactly(
                "note Steve " + STEVE + " hacking #" + ban.id() + " by Mod: Hacking",
                "say step 1: 1d");
    }

    @Test
    void draftsMustMatchTheTemplate() {
        this.create(template("spam", PunishmentType.MUTE).build());

        Assertions.assertThat(this.punish(STEVE, "spam")).isInstanceOfSatisfying(
                PunishResult.Failure.class, failure -> Assertions.assertThat(failure.reason())
                        .isEqualTo(PunishResult.Failure.Reason.UNKNOWN_TEMPLATE));
    }

    @Test
    void editingIsValidatedVersionedAndSynced() {
        final SakuraTemplateInfo created = this.create(template("spam", PunishmentType.MUTE)
                .ladder(ladder(Duration.ofHours(1), Duration.ofDays(1))).build());

        Assertions.assertThat(this.templates.create(template("spam", PunishmentType.MUTE)
                .build(), CONSOLE).join()).isInstanceOf(AdminResult.ValidationFailed.class);
        // Built without the builder's checks: the service checks again.
        final TemplateDefinition loud = template("loud", PunishmentType.MUTE).shadow(true).build();
        Assertions.assertThat(this.templates.create(new TemplateDefinition(loud.name(),
                PunishmentType.BAN, false, true, null, loud.reason(), loud.ladder(), null, false,
                false, List.of(), Map.of(), null), CONSOLE).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThatThrownBy(() -> template("k", PunishmentType.KICK)
                        .ladder(ladder(Duration.ofDays(1))).build())
                .isInstanceOf(ValidationException.class);
        Assertions.assertThat(this.templates.reorderSteps("spam", List.of(0, 0), 1, CONSOLE)
                .join()).isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.templates.reorderSteps("spam", List.of(1, 0), 7, CONSOLE)
                .join()).isInstanceOf(AdminResult.Conflict.class);

        final AdminResult<SakuraTemplateInfo> reordered =
                this.templates.reorderSteps("spam", List.of(1, 0), created.version(), CONSOLE)
                        .join();
        Assertions.assertThat(reordered).isInstanceOfSatisfying(AdminResult.Ok.class, ok -> {
            final SakuraTemplateInfo value = (SakuraTemplateInfo) ok.value();
            Assertions.assertThat(value.ladder().getFirst().duration())
                    .hasValue(Duration.ofDays(1));
            Assertions.assertThat(value.version()).isEqualTo(2);
        });
        Assertions.assertThat(this.templates.find("SPAM").orElseThrow().version())
                .isEqualTo(2);
        Assertions.assertThat(this.templates.delete("spam", 2, CONSOLE).join())
                .isInstanceOf(AdminResult.Ok.class);
        Assertions.assertThat(this.templates.templates()).isEmpty();
        Assertions.assertThat(this.templates.delete("spam", 2, CONSOLE).join())
                .isInstanceOf(AdminResult.NotFound.class);

        Assertions.assertThat(this.published).filteredOn(SyncPacket.EntityChangedPacket.class
                ::isInstance).containsOnly(new SyncPacket.EntityChangedPacket(
                        SubjectType.TEMPLATE)).hasSize(3);
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
        final SakuraTemplateInfo spam = this.templates.find("spam").orElseThrow();
        this.templates.delete("spam", spam.version(), CONSOLE).join();

        this.templates.start().join();
        Assertions.assertThat(this.templates.template("spam")).isEmpty();

        Files.writeString(this.dir.resolve("templates.yml"),
                """
                templates:
                  spam:
                    type: mute
                    reason: Reason
                    ladder: [1h]
                  hacking:
                    type: ban
                    reason: Changed
                    ladder: [permanent]
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
