package net.serlith.sakurabans.core.template;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PunishmentCreateEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.storage.repository.NewTemplateUse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S64: concurrent applies of a ladder to one victim get consecutive steps. */
class ConcurrentLadderTest extends ServiceTest {

    private static final PlayerVictim VICTIM = PlayerVictim.of(STEVE);

    @BeforeEach
    void ladder() {
        this.templates.create(TemplateBuilder.ladder("race").reason("Race")
                .steps(List.of(
                        // Warnings stack, so no step is refused as ALREADY_PUNISHED.
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open(), "first",
                                null, List.of()),
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open(), "second",
                                null, List.of()),
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open(), "third",
                                null, List.of()),
                        TemplateStep.of(PunishmentType.BAN,
                                TemplateDuration.fixed(Duration.ofDays(1)), "fourth", null,
                                List.of())))
                .build(), CONSOLE).join();
    }

    private CompletableFuture<LadderResult> apply() {
        return this.service.applyLadder(LadderRequest.builder("race", VICTIM, MODERATOR).build(),
                PunishOptions.defaults());
    }

    private static int step(final LadderResult result) {
        return ((LadderResult.Punished) result).step();
    }

    private long uses() {
        return this.templateRepository.history(Set.of("race"), VICTIM, false, null,
                this.clock.instant()).join().getOrDefault("race", 0L);
    }

    /** Another use of the ladder on the victim, stored from outside the apply. */
    private void sneakInUse() {
        this.templateRepository.recordUse(new NewTemplateUse("race", 0, VICTIM, CONSOLE,
                this.clock.instant()), null).join();
    }

    @Test
    void twoConcurrentAppliesGetConsecutiveSteps() throws Exception {
        // Both applies hold in the create event, after counting and before storing, so both
        // count step 0. The storing transaction serializes them; the second counts again.
        final CountDownLatch counted = new CountDownLatch(2);
        final AtomicInteger creates = new AtomicInteger();
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.NORMAL, event -> {
            creates.incrementAndGet();
            counted.countDown();
            try {
                Assertions.assertThat(counted.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        final CompletableFuture<LadderResult> first = this.apply();
        final CompletableFuture<LadderResult> second = this.apply();
        final List<Integer> steps = List.of(step(first.get(20, TimeUnit.SECONDS)),
                step(second.get(20, TimeUnit.SECONDS)));

        Assertions.assertThat(steps).containsExactlyInAnyOrder(0, 1);
        Assertions.assertThat(creates).as("the later one applied again").hasValue(3);
        Assertions.assertThat(this.uses()).isEqualTo(2);
        Assertions.assertThat(List.of(first.join(), second.join()))
                .extracting(result -> ((LadderResult.Punished) result).punishment())
                .extracting(Punishment::reason)
                .containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void stepTakenMeanwhileMakesTheApplyCountAgain() {
        final AtomicInteger creates = new AtomicInteger();
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.NORMAL, event -> {
            if (creates.incrementAndGet() == 1) {
                this.sneakInUse();
            }
        });

        final LadderResult result = this.apply().join();

        Assertions.assertThat(step(result)).as("counted again: one use more").isEqualTo(1);
        Assertions.assertThat(((LadderResult.Punished) result).punishment().reason())
                .isEqualTo("second");
        Assertions.assertThat(creates).hasValue(2);
        Assertions.assertThat(this.uses()).isEqualTo(2);
        Assertions.assertThat(this.log.entries("debug"))
                .anySatisfy(entry -> Assertions.assertThat(entry.format())
                        .contains("Step 1 of race", "taken meanwhile", "now 2"));
    }

    @Test
    void afterThreeAttemptsTheLastCountIsStored() {
        final AtomicInteger creates = new AtomicInteger();
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.NORMAL, event -> {
            creates.incrementAndGet();
            this.sneakInUse();
        });

        final LadderResult result = this.apply().join();

        // Counted at 0, 1 and 2 uses; each time one more use came in before storing.
        Assertions.assertThat(step(result)).isEqualTo(2);
        Assertions.assertThat(creates).hasValue(3);
        Assertions.assertThat(this.uses()).isEqualTo(4);
        Assertions.assertThat(this.log.entries("warn"))
                .anySatisfy(entry -> Assertions.assertThat(entry.format())
                        .contains("race", "changed during 3 attempts", "step 3"));
    }
}
