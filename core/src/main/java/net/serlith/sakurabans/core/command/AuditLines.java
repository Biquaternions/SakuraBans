package net.serlith.sakurabans.core.command;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.util.AddressMasks;
import net.serlith.sakurabans.core.util.Uuids;
import org.jspecify.annotations.Nullable;

public final class AuditLines {

    private static final int SHORT_HASH = 8;
    private static final int SUMMARY = 80;

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final Placeholders placeholders;
    private final Formats formats;
    private final DisplayIdCodec displayIds;
    private final NameCache names;

    public AuditLines(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final Placeholders placeholders, final Formats formats,
            final DisplayIdCodec displayIds, final NameCache names) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.placeholders = placeholders;
        this.formats = formats;
        this.displayIds = displayIds;
        this.names = names;
    }

    public TagResolver values(final CommandActor actor, final AuditEntry entry) {
        final Locale locale = this.runner.locale(actor);
        final boolean show = this.replies.showAddresses(actor);
        final String old = this.summary(entry.oldValue(), show);
        final String updated = this.summary(entry.newValue(), show);
        return TagResolver.resolver(
                Placeholder.unparsed("at", this.formats.instant(entry.at(), locale)),
                Placeholder.unparsed("action", entry.action().name().toLowerCase(Locale.ROOT)),
                Placeholder.unparsed("actor",
                        this.placeholders.operator(entry.actor().operator(), locale)),
                Placeholder.unparsed("source", entry.actor().source()),
                Placeholder.unparsed("old", old.isEmpty() ? "" : old + " → "),
                Placeholder.unparsed("new", updated),
                Placeholder.component("subject", this.subject(actor, entry, show)));
    }

    /** The subject as an export or a log writes it: the same masking, as plain text. */
    public String subjectText(final AuditEntry entry, final boolean show) {
        return switch (entry.subjectType()) {
            case ADDRESS_HASH -> shortHash(entry.subjectId());
            case PLAYER, ALLOW_LIST -> this.player(entry.subjectId(), show);
            case PUNISHMENT -> this.punishmentLabel(entry.subjectId());
            default -> show ? entry.subjectId() : AddressMasks.censor(entry.subjectId());
        };
    }

    /** The subject as an export holds it: the raw id, masked, or a hash's short label. */
    public String exportSubject(final AuditEntry entry, final boolean show) {
        return entry.subjectType() == SubjectType.ADDRESS_HASH
                ? shortHash(entry.subjectId()) : this.text(entry.subjectId(), show);
    }

    public String text(final String value, final boolean show) {
        return show ? value : AddressMasks.censor(value);
    }

    private String summary(final @Nullable String value, final boolean show) {
        if (value == null) {
            return "";
        }
        final String masked = this.text(value, show);
        return masked.length() <= SUMMARY ? masked : masked.substring(0, SUMMARY) + "…";
    }

    private Component subject(final CommandActor actor, final AuditEntry entry,
            final boolean show) {
        final MessageKey key = switch (entry.subjectType()) {
            case PUNISHMENT -> MessageKey.AUDIT_SUBJECT_PUNISHMENT;
            case TEMPLATE -> MessageKey.AUDIT_SUBJECT_TEMPLATE;
            case SETTINGS -> MessageKey.AUDIT_SUBJECT_SETTINGS;
            case MESSAGE -> MessageKey.AUDIT_SUBJECT_MESSAGE;
            case PLAYER -> MessageKey.AUDIT_SUBJECT_PLAYER;
            case ALLOW_LIST -> MessageKey.AUDIT_SUBJECT_ALLOW_LIST;
            case IMPORT -> MessageKey.AUDIT_SUBJECT_IMPORT;
            case ADDRESS_HASH -> MessageKey.AUDIT_SUBJECT_ADDRESS_HASH;
            case VICTIM -> MessageKey.AUDIT_SUBJECT_VICTIM;
        };
        final Component rendered = this.runner.render(actor, key, Placeholder.unparsed("id",
                this.subjectText(entry, show)));
        if (entry.subjectType() != SubjectType.PUNISHMENT || this.id(entry.subjectId()) < 1) {
            return rendered;
        }
        final String root = this.registrar.label(SakuraCommands.ROOT).orElse(SakuraCommands.ROOT);
        return rendered.clickEvent(ClickEvent.runCommand("/" + root + " info "
                + this.displayIds.display(this.id(entry.subjectId()))));
    }

    private String punishmentLabel(final String subjectId) {
        final long id = this.id(subjectId);
        return id < 1 ? subjectId : this.displayIds.display(id);
    }

    private long id(final String subjectId) {
        try {
            return Long.parseLong(subjectId);
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private String player(final String subjectId, final boolean show) {
        final Optional<UUID> uuid = Uuids.parse(subjectId);
        if (uuid.isPresent()) {
            return this.names.name(uuid.get()).orElse(subjectId);
        }
        return show ? subjectId : AddressMasks.censor(subjectId);
    }

    private static String shortHash(final String hash) {
        return hash.length() <= SHORT_HASH ? hash : hash.substring(0, SHORT_HASH);
    }
}
