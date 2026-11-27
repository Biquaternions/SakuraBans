package net.serlith.sakurabans.core.template;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.event.TemplateAppliedEvent;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code TemplateAppliedEvent} with its actor and reason, the actions run by the service
 * from the definition that was applied, and {@code nextStep} at a scope.
 */
class TemplateAppliedTest extends ServiceTest {

    @BeforeEach
    void templates() {
        this.names.put(STEVE, "Steve");
        this.templates.create(TemplateBuilder.preset("spam", PunishmentType.MUTE)
                .reason("Spam").actions(List.of("say {victim} muted for {reason}")).build(),
                CONSOLE).join();
        this.templates.create(TemplateBuilder.ladder("conduct").reason("Conduct")
                .steps(List.of(
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                        TemplateStep.of(null, TemplateDuration.open(), "Watched", null,
                                List.of("say {victim} is watched: {reason}")),
                        TemplateStep.of(PunishmentType.BAN,
                                TemplateDuration.fixed(Duration.ofDays(1)))))
                .build(), CONSOLE).join();
    }

    private LadderResult ladder(final @Nullable String reason) {
        return this.service.applyLadder(LadderRequest.builder("conduct", PlayerVictim.of(STEVE),
                MODERATOR).reason(reason).build(), PunishOptions.defaults()).join();
    }

    @Test
    void everyUseFiresTheAppliedEventWithItsActorAndReason() {
        this.service.punish(DraftPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(STEVE),
                MODERATOR).template("spam").build(), PunishOptions.defaults()).join();
        this.ladder(null);
        this.ladder("swearing");

        Assertions.assertThat(this.events(TemplateAppliedEvent.class)).hasSize(3)
                .allSatisfy(event -> Assertions.assertThat(event.actor()).isEqualTo(MODERATOR))
                .extracting(TemplateAppliedEvent::reason)
                // The preset's punishment, the ladder's own reason, the request's.
                .containsExactly("Spam", "Conduct", "swearing");
        final TemplateAppliedEvent actionsOnly = this.events(TemplateAppliedEvent.class).get(2);
        Assertions.assertThat(actionsOnly.punishment()).isEmpty();
        Assertions.assertThat(actionsOnly.template().step()).hasValue(1);
    }

    @Test
    void actionsRunAfterTheEventFromTheDefinitionThatWasApplied() {
        final List<Integer> commandsSeenByHandlers = new ArrayList<>();
        this.bus.subscribe(TemplateAppliedEvent.class, EventPriority.MONITOR, event ->
                commandsSeenByHandlers.add(this.platform.consoleCommands().size()));
        // The preset is gone by the time the use is announced: its actions still run, from the
        // definition the punishment was made with.
        this.bus.subscribe(PunishmentCreatedEvent.class, EventPriority.NORMAL, event ->
                this.templates.find("spam").ifPresent(spam -> this.templates.delete(null,
                        "spam", spam.version(), CONSOLE).join()));

        final PunishResult result = this.service.punish(DraftPunishment.builder(
                PunishmentType.MUTE, PlayerVictim.of(STEVE), MODERATOR).template("spam")
                .build(), PunishOptions.defaults()).join();

        Assertions.assertThat(result).isInstanceOf(PunishResult.Success.class);
        Assertions.assertThat(this.templates.find("spam")).isEmpty();
        Assertions.assertThat(commandsSeenByHandlers).as("the event's handlers run first")
                .containsExactly(0);
        Assertions.assertThat(this.platform.consoleCommands())
                .containsExactly("say Steve muted for Spam");

        this.ladder(null);
        this.ladder("swearing");
        Assertions.assertThat(this.platform.consoleCommands())
                .containsExactly("say Steve muted for Spam", "say Steve is watched: swearing");
    }

    @Test
    void nextStepAtScopeUsesTheDefinitionInForceThere() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"),
                """
                server:
                  name: lobby
                scopes:
                  categories:
                    pvp: [arena]
                """);
        this.config.reload();
        // One step only: the variant is past its end where the global ladder isn't.
        this.templates.create(TemplateBuilder.ladder("conduct").reason("Conduct in pvp")
                .steps(List.of(TemplateStep.of(PunishmentType.KICK, TemplateDuration.open())))
                .variant(CategoryScope.of("pvp")).build(), CONSOLE).join();
        this.ladder(null);
        this.ladder(null);
        final PlayerVictim steve = PlayerVictim.of(STEVE);

        Assertions.assertThat(this.templates.nextStep("conduct", steve).join()).hasValue(2);
        Assertions.assertThat(this.templates.nextStep("conduct", steve, null).join())
                .hasValue(2);
        Assertions.assertThat(this.templates.nextStep("conduct", steve, GlobalScope.INSTANCE)
                .join()).hasValue(2);
        Assertions.assertThat(this.templates.nextStep("conduct", steve, CategoryScope.of("pvp"))
                .join()).hasValue(0);
        // A server of the category, as /punish --server arena would pick it.
        Assertions.assertThat(this.templates.nextStep("conduct", steve, ServerScope.of("arena"))
                .join()).hasValue(0);
        Assertions.assertThat(this.templates.nextStep("conduct", steve, ServerScope.of("hub"))
                .join()).hasValue(2);
        Assertions.assertThat(this.templates.nextStep("spam", steve, CategoryScope.of("pvp"))
                .join()).as("a preset").isEmpty();
        Assertions.assertThat(this.templates.nextStep("nothing", steve, null).join()).isEmpty();
    }
}
