package net.serlith.sakurabans.core.event;

import java.util.Optional;
import net.serlith.sakurabans.api.event.EvidenceAttachedEvent;
import net.serlith.sakurabans.api.event.EvidenceDetachedEvent;
import net.serlith.sakurabans.api.event.PunishmentEditEvent;
import net.serlith.sakurabans.api.event.PunishmentEditedEvent;
import net.serlith.sakurabans.api.event.PunishmentExpungeEvent;
import net.serlith.sakurabans.api.event.PunishmentExpungedEvent;
import net.serlith.sakurabans.api.event.PunishmentVoidEvent;
import net.serlith.sakurabans.api.event.PunishmentVoidedEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.EditRequest;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import org.jspecify.annotations.Nullable;

public final class SakuraPunishmentChangeEvents {

    private SakuraPunishmentChangeEvents() {
    }

    public static final class Edit implements PunishmentEditEvent {

        private final Punishment punishment;
        private final EditRequest request;
        private volatile boolean cancelled;

        public Edit(final Punishment punishment, final EditRequest request) {
            this.punishment = punishment;
            this.request = request;
        }

        @Override
        public Punishment punishment() {
            return this.punishment;
        }

        @Override
        public EditRequest request() {
            return this.request;
        }

        @Override
        public boolean cancelled() {
            return this.cancelled;
        }

        @Override
        public void cancelled(final boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    public record Edited(Punishment previous, Punishment punishment, PunishmentEdit edit,
            boolean remote) implements PunishmentEditedEvent {
    }

    public static final class Voiding implements PunishmentVoidEvent {

        private final Punishment punishment;
        private final Actor actor;
        private final @Nullable String reason;
        private volatile boolean cancelled;

        public Voiding(final Punishment punishment, final Actor actor,
                final @Nullable String reason) {
            this.punishment = punishment;
            this.actor = actor;
            this.reason = reason;
        }

        @Override
        public Punishment punishment() {
            return this.punishment;
        }

        @Override
        public Actor actor() {
            return this.actor;
        }

        @Override
        public Optional<String> reason() {
            return Optional.ofNullable(this.reason);
        }

        @Override
        public boolean cancelled() {
            return this.cancelled;
        }

        @Override
        public void cancelled(final boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    public record Voided(Punishment punishment, boolean remote) implements PunishmentVoidedEvent {
    }

    public static final class Expunge implements PunishmentExpungeEvent {

        private final Punishment punishment;
        private final Actor actor;
        private volatile boolean cancelled;

        public Expunge(final Punishment punishment, final Actor actor) {
            this.punishment = punishment;
            this.actor = actor;
        }

        @Override
        public Punishment punishment() {
            return this.punishment;
        }

        @Override
        public Actor actor() {
            return this.actor;
        }

        @Override
        public boolean cancelled() {
            return this.cancelled;
        }

        @Override
        public void cancelled(final boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    public record Expunged(Punishment punishment, boolean remote)
            implements PunishmentExpungedEvent {
    }

    public record EvidenceAttached(Punishment punishment, Evidence evidence, boolean remote)
            implements EvidenceAttachedEvent {
    }

    public record EvidenceDetached(Punishment punishment, Evidence evidence, boolean remote)
            implements EvidenceDetachedEvent {
    }

}
