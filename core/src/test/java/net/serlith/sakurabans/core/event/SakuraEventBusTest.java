package net.serlith.sakurabans.core.event;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.serlith.sakurabans.api.event.Cancellable;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PunishmentCreateEvent;
import net.serlith.sakurabans.api.event.SakuraEvent;
import net.serlith.sakurabans.api.event.Subscription;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.RecordingLogger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SakuraEventBusTest {

    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    private final RecordingLogger log = new RecordingLogger();
    private final SakuraEventBus bus = new SakuraEventBus(this.log.logger());

    private static SakuraPunishmentCreateEvent createEvent() {
        return new SakuraPunishmentCreateEvent(DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(UUID.randomUUID()), CONSOLE).build(), PunishOptions.defaults());
    }

    @Test
    void handlersRunByPriorityThenSubscriptionOrder() {
        final List<String> calls = new CopyOnWriteArrayList<>();
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.MONITOR,
                event -> calls.add("monitor"));
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.HIGH,
                event -> calls.add("high"));
        this.bus.subscribe(PunishmentCreateEvent.class, event -> calls.add("normal-1"));
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.LOWEST,
                event -> calls.add("lowest"));
        this.bus.subscribe(PunishmentCreateEvent.class, event -> calls.add("normal-2"));

        this.bus.post(createEvent());

        Assertions.assertThat(calls)
                .containsExactly("lowest", "normal-1", "normal-2", "high", "monitor");
    }

    @Test
    void supertypesReceiveSubtypes() {
        final List<SakuraEvent> all = new CopyOnWriteArrayList<>();
        final List<SakuraEvent> cancellable = new CopyOnWriteArrayList<>();
        this.bus.subscribe(SakuraEvent.class, all::add);
        this.bus.subscribe(Cancellable.class, cancellable::add);

        this.bus.post(createEvent());
        this.bus.post(new SakuraPunishmentExpiredEvent(Fixtures.ban(1)));

        Assertions.assertThat(all).hasSize(2);
        Assertions.assertThat(cancellable).singleElement()
                .isInstanceOf(PunishmentCreateEvent.class);
    }

    @Test
    void laterHandlersSeeTheCancellationAndMayUndoIt() {
        final List<Boolean> seen = new CopyOnWriteArrayList<>();
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.LOW,
                event -> event.cancelled(true));
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.NORMAL,
                event -> seen.add(event.cancelled()));
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.HIGHEST,
                event -> event.cancelled(false));

        final SakuraPunishmentCreateEvent event = this.bus.post(createEvent());

        Assertions.assertThat(seen).containsExactly(true);
        Assertions.assertThat(event.cancelled()).isFalse();
    }

    @Test
    void monitorHandlersCantChangeTheOutcome() {
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.HIGH,
                event -> event.cancelled(true));
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.MONITOR,
                event -> event.cancelled(false));

        final SakuraPunishmentCreateEvent event = this.bus.post(createEvent());

        Assertions.assertThat(event.cancelled()).isTrue();
        Assertions.assertThat(this.log.entries("warn")).hasSize(1);
    }

    @Test
    void failingHandlersAreLoggedAndTheOthersStillRun() {
        final List<String> calls = new CopyOnWriteArrayList<>();
        this.bus.subscribe(PunishmentCreateEvent.class, EventPriority.LOW, event -> {
            throw new IllegalStateException("boom");
        });
        this.bus.subscribe(PunishmentCreateEvent.class, event -> calls.add("after"));

        this.bus.post(createEvent());

        Assertions.assertThat(calls).containsExactly("after");
        Assertions.assertThat(this.log.entries("error")).hasSize(1);
    }

    @Test
    void unsubscribedHandlersNoLongerRun() {
        final List<String> calls = new CopyOnWriteArrayList<>();
        final Subscription subscription =
                this.bus.subscribe(PunishmentCreateEvent.class, event -> calls.add("x"));
        Assertions.assertThat(subscription.active()).isTrue();

        subscription.unsubscribe();
        subscription.unsubscribe();
        this.bus.post(createEvent());

        Assertions.assertThat(subscription.active()).isFalse();
        Assertions.assertThat(calls).isEmpty();
    }
}
