package net.serlith.sakurabans.core.template;

import java.net.InetAddress;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.event.EntityChangedEvent;
import net.serlith.sakurabans.api.event.TemplateStepEvent;
import net.serlith.sakurabans.api.integration.EvidenceCapturer;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.template.TemplateDefinition;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SakuraTemplateServiceTest extends ServiceTest {

    private static List<TemplateStep> ladder(final Duration... steps) {
        return Arrays.stream(steps).map(TemplateStep::of).toList();
    }

    private static TemplateDefinition.Builder template(final String name, final String kind) {
        return TemplateDefinition.builder(name, kind).reason("Reason");
    }

    private TemplateInfo create(final TemplateDefinition definition) {
        return ((AdminResult.Ok<TemplateInfo>) this.templates.create(definition, CONSOLE).join())
                .value();
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
        this.create(TemplateDefinition.builder("hacking", "ban").reason("Hacking")
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
        this.create(template("spam", "ban")
                .ladder(ladder(Duration.ofHours(1), Duration.ofDays(1)))
                .ladderExpiry(Duration.ofDays(30)).build());
        this.banAndLift(STEVE, "spam");

        Assertions.assertThat(this.templates.preview("spam", PlayerVictim.of(STEVE)).join())
                .hasValueSatisfying(preview -> Assertions.assertThat(preview.step())
                        .isEqualTo(1));
        this.now.set(this.now.get().plus(Duration.ofDays(31)));
        Assertions.assertThat(this.templates.preview("spam", PlayerVictim.of(STEVE)).join())
                .hasValueSatisfying(preview -> {
                    Assertions.assertThat(preview.step()).isZero();
                    Assertions.assertThat(preview.duration()).hasValue(Duration.ofHours(1));
                });
        Assertions.assertThat(this.templates.preview("nothing", PlayerVictim.of(STEVE)).join())
                .isEmpty();
    }

    @Test
    void groupMatesCountWithTheirWeight() {
        final List<TemplateStep> five = ladder(Duration.ofHours(1), Duration.ofHours(2),
                Duration.ofHours(3), Duration.ofHours(4), Duration.ofHours(5));
        this.create(template("hacking", "ban").ladder(five)
                .groups(Map.of("cheating", 2)).build());
        this.create(template("xray", "ban").ladder(five)
                .groups(Map.of("cheating", 1)).build());
        this.create(template("spam", "ban").ladder(five).build());

        this.banAndLift(STEVE, "hacking");
        this.banAndLift(STEVE, "spam");

        // hacking (worth 2) counts on the xray ladder; spam shares no group.
        Assertions.assertThat(this.templates.preview("xray", PlayerVictim.of(STEVE)).join()
                .orElseThrow().step()).isEqualTo(2);
        this.banAndLift(STEVE, "xray");
        Assertions.assertThat(this.templates.preview("hacking", PlayerVictim.of(STEVE)).join()
                .orElseThrow().step()).isEqualTo(3);
        Assertions.assertThat(this.templates.preview("spam", PlayerVictim.of(STEVE)).join()
                .orElseThrow().step()).isEqualTo(1);
    }

    @Test
    void sharedProgressFollowsAddresses() throws Exception {
        final InetAddress shared = InetAddress.getByName("203.0.113.9");
        this.players.recordConnection(STEVE, "Steve", this.now.get(), shared, null).join();
        this.players.recordConnection(ALEX, "Alex", this.now.get(), shared, null).join();
        this.create(template("alting", "ban").addressShared(true)
                .ladder(ladder(Duration.ofDays(1), Duration.ofDays(2))).build());
        this.create(template("solo", "ban")
                .ladder(ladder(Duration.ofDays(1), Duration.ofDays(2))).build());

        this.banAndLift(STEVE, "alting");
        this.banAndLift(STEVE, "solo");

        Assertions.assertThat(length(this.banAndLift(ALEX, "alting")))
                .isEqualTo(Duration.ofDays(2));
        Assertions.assertThat(length(this.banAndLift(ALEX, "solo")))
                .isEqualTo(Duration.ofDays(1));
    }

    @Test
    void evidenceIsCapturedAndRequired() {
        this.create(template("chat", "ban")
                .captureEvidence(Set.of("hydra:chat-snapshot"))
                .requiredEvidence(Set.of("hydra:chat-snapshot")).build());

        Assertions.assertThat(this.punish(STEVE, "chat")).isInstanceOfSatisfying(
                PunishResult.Failure.class, failure -> Assertions.assertThat(failure.reason())
                        .isEqualTo(PunishResult.Failure.Reason.EVIDENCE_REQUIRED));

        this.integrations.register(new EvidenceCapturer() {
            @Override
            public String type() {
                return "hydra:chat-snapshot";
            }

            @Override
            public CompletableFuture<EvidenceRef> capture(final UUID target, final Actor actor) {
                return CompletableFuture.completedFuture(
                        EvidenceRef.of("hydra:chat-snapshot:" + target));
            }
        });
        this.integrations.register(new EvidenceCapturer() {
            @Override
            public String type() {
                return "hydra:chat-snapshot";
            }

            @Override
            public CompletableFuture<EvidenceRef> capture(final UUID target, final Actor actor) {
                throw new IllegalStateException("broken capturer");
            }
        });

        Assertions.assertThat(this.punish(STEVE, "chat")).isInstanceOfSatisfying(
                PunishResult.Success.class, success -> Assertions.assertThat(
                        success.punishment().evidence()).singleElement().satisfies(evidence ->
                        Assertions.assertThat(evidence.ref().uri())
                                .isEqualTo("hydra:chat-snapshot:" + STEVE)));
    }

    @Test
    void actionsRunWithPlaceholders() {
        this.names.put(STEVE, "Steve");
        this.create(TemplateDefinition.builder("hacking", "ban").reason("Hacking")
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
        this.create(template("spam", "mute").build());

        Assertions.assertThat(this.punish(STEVE, "spam")).isInstanceOfSatisfying(
                PunishResult.Failure.class, failure -> Assertions.assertThat(failure.reason())
                        .isEqualTo(PunishResult.Failure.Reason.UNKNOWN_TEMPLATE));
        Assertions.assertThat(this.service.punish(DraftPunishment.builder(PunishmentType.MUTE,
                        PlayerVictim.of(STEVE), MODERATOR).template("spam").kind("shadow-mute")
                        .build(), PunishOptions.defaults()).join())
                .isInstanceOfSatisfying(PunishResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(PunishResult.Failure.Reason.UNKNOWN_KIND));
    }

    @Test
    void editingIsValidatedVersionedSyncedAndAnnounced() {
        final TemplateInfo created = this.create(template("spam", "mute")
                .ladder(ladder(Duration.ofHours(1), Duration.ofDays(1))).build());

        Assertions.assertThat(this.templates.create(template("spam", "mute")
                .build(), CONSOLE).join()).isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.templates.create(template("x", "nothing")
                .build(), CONSOLE).join()).isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.templates.create(template("k", "kick")
                        .ladder(ladder(Duration.ofDays(1))).build(), CONSOLE).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.templates.reorderSteps("spam", List.of(0, 0), 1, CONSOLE)
                .join()).isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.templates.reorderSteps("spam", List.of(1, 0), 7, CONSOLE)
                .join()).isInstanceOf(AdminResult.Conflict.class);

        final AdminResult<TemplateInfo> reordered =
                this.templates.reorderSteps("spam", List.of(1, 0), created.version(), CONSOLE)
                        .join();
        Assertions.assertThat(reordered).isInstanceOfSatisfying(AdminResult.Ok.class, ok -> {
            final TemplateInfo value = (TemplateInfo) ok.value();
            Assertions.assertThat(value.ladder().getFirst().duration())
                    .hasValue(Duration.ofDays(1));
            Assertions.assertThat(value.version()).isEqualTo(2);
        });
        Assertions.assertThat(this.templates.template("SPAM").orElseThrow().version())
                .isEqualTo(2);
        Assertions.assertThat(this.templates.delete("spam", 2, CONSOLE).join())
                .isInstanceOf(AdminResult.Ok.class);
        Assertions.assertThat(this.templates.templates()).isEmpty();
        Assertions.assertThat(this.templates.delete("spam", 2, CONSOLE).join())
                .isInstanceOf(AdminResult.NotFound.class);

        Assertions.assertThat(this.events(EntityChangedEvent.TemplateChanged.class))
                .extracting(EntityChangedEvent::change)
                .containsExactly(ChangeType.CREATED, ChangeType.UPDATED, ChangeType.DELETED);
        Assertions.assertThat(this.published).filteredOn(SyncPacket.EntityChangedPacket.class
                ::isInstance).hasSize(3);
    }

    @Test
    void forbiddenActorsChangeNothing() {
        this.actionCheck = (actor, action) -> CompletableFuture.completedFuture(
                Decision.deny(Decision.Denial.NO_PERMISSION, null));

        Assertions.assertThat(this.templates.create(template("spam", "mute")
                .build(), MODERATOR).join()).isInstanceOf(AdminResult.Forbidden.class);
        Assertions.assertThat(this.templates.templates()).isEmpty();
    }

    @Test
    void otherInstancesReloadAndAnnounceRemotely() {
        // Written "elsewhere": stored, but not in this instance's memory yet.
        this.templateRepository.create(template("spam", "mute").build(),
                PunishmentType.MUTE, CONSOLE, this.now.get()).join();
        Assertions.assertThat(this.templates.template("spam")).isEmpty();

        this.templates.changed(new SyncPacket.EntityChangedPacket(SubjectType.TEMPLATE, "spam",
                ChangeType.CREATED, 1, CONSOLE)).join();

        Assertions.assertThat(this.templates.template("spam")).isPresent();
        Assertions.assertThat(this.events(EntityChangedEvent.TemplateChanged.class))
                .singleElement().satisfies(event -> {
                    Assertions.assertThat(event.remote()).isTrue();
                    Assertions.assertThat(event.template()).isPresent();
                });
    }

    @Test
    void theShippedFileIsImportedOnceAndMergedOnRequest() throws Exception {
        this.templates.start().join();
        Assertions.assertThat(this.templates.templates()).extracting(TemplateInfo::name)
                .contains("hacking", "spam", "xray");
        final TemplateInfo spam = this.templates.template("spam").orElseThrow();
        this.templates.delete("spam", spam.version(), CONSOLE).join();

        this.templates.start().join();
        Assertions.assertThat(this.templates.template("spam")).isEmpty();

        Files.writeString(this.dir.resolve("templates.yml"),
                """
                templates:
                  spam:
                    kind: mute
                    reason: Reason
                    ladder: [1h]
                  hacking:
                    kind: ban
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
