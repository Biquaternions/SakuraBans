package net.serlith.sakurabans.core.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.configuration.SettingsSchema;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import org.jspecify.annotations.Nullable;

/** The lines a sender, or a session's creator, sees for an application (protocol 7.4). */
public final class EditorLines {

    /** Longer values are cut, so one change stays one line. */
    public static final int MAX_VALUE = 60;

    private final MessageService messages;
    private final Platform platform;

    public EditorLines(final MessageService messages, final Platform platform) {
        this.messages = messages;
        this.platform = platform;
    }

    /**
     * Tells a session's creator on this instance (protocol 7.5): a player while online here, a
     * console creator in this console. False when the creator isn't here.
     */
    public boolean toCreator(final Operator creator, final EditorService.Applied applied,
            final MessageKey header, final TagResolver... resolvers) {
        final Audience audience;
        final Locale locale;
        if (creator instanceof PlayerOperator player) {
            final OnlinePlayer online = this.platform.player(player.uuid()).orElse(null);
            if (online == null) {
                return false;
            }
            audience = online;
            locale = online.locale().orElseGet(this.messages::defaultLocale);
        } else if (creator instanceof ConsoleOperator) {
            audience = this.platform.console();
            locale = this.messages.defaultLocale();
        } else {
            return false;
        }
        audience.sendMessage(this.line(header, locale, resolvers));
        this.lines(applied, locale).forEach(audience::sendMessage);
        return true;
    }

    /** Whether both name the same sender: one player, or the console. */
    public static boolean same(final Operator first, final Operator second) {
        if (first instanceof PlayerOperator one && second instanceof PlayerOperator other) {
            return one.uuid().equals(other.uuid());
        }
        return first instanceof ConsoleOperator && second instanceof ConsoleOperator;
    }

    public List<Component> lines(final EditorService.Applied applied, final Locale locale) {
        final List<Component> lines = new ArrayList<>();
        switch (applied) {
            case EditorService.Applied.NotConfigured notConfigured ->
                    lines.add(this.line(MessageKey.EDITOR_NOT_SET_UP, locale));
            case EditorService.Applied.Failed failed ->
                    lines.add(this.line(problem(failed.problem()), locale));
            case EditorService.Applied.UnknownSession unknown ->
                    lines.add(this.line(MessageKey.EDITOR_APPLY_SESSION_UNKNOWN, locale));
            case EditorService.Applied.Unreadable unreadable ->
                    lines.add(this.line(MessageKey.EDITOR_APPLY_UNREADABLE, locale));
            case EditorService.Applied.Replayed replayed ->
                    lines.add(this.line(MessageKey.EDITOR_APPLY_REPLAYED, locale));
            case EditorService.Applied.OtherFormat other ->
                    lines.add(this.line(MessageKey.EDITOR_APPLY_OTHER_FORMAT, locale,
                            Placeholder.unparsed("format", Integer.toString(other.format()))));
            case EditorService.Applied.Invalid invalid -> {
                lines.add(this.line(MessageKey.EDITOR_APPLY_INVALID, locale,
                        Placeholder.unparsed("count", Integer.toString(invalid.errors().size()))));
                invalid.errors().forEach(error -> lines.add(this.line(
                        MessageKey.EDITOR_APPLY_INVALID_ENTRY, locale,
                        Placeholder.unparsed("path", error.path()),
                        Placeholder.unparsed("message", error.message()))));
            }
            case EditorService.Applied.Forbidden forbidden ->
                    lines.add(this.line(MessageKey.EDITOR_APPLY_FORBIDDEN, locale,
                            Placeholder.unparsed("sections", String.join(", ", forbidden
                                    .sections().stream().map(EditorService.Section::id)
                                    .toList()))));
            case EditorService.Applied.Done done -> this.done(done, locale, lines);
        }
        return lines;
    }

    public Component line(final MessageKey key, final Locale locale,
            final TagResolver... resolvers) {
        return this.messages.render(key, locale, resolvers);
    }

    public static MessageKey problem(final EditorClient.Problem problem) {
        return switch (problem) {
            case NOT_CONFIGURED -> MessageKey.EDITOR_NOT_SET_UP;
            case UNREACHABLE -> MessageKey.EDITOR_FAILED_UNREACHABLE;
            case UNAUTHORIZED -> MessageKey.EDITOR_FAILED_UNAUTHORIZED;
            case TOO_LARGE -> MessageKey.EDITOR_FAILED_TOO_LARGE;
            case RATE_LIMITED -> MessageKey.EDITOR_FAILED_RATE_LIMITED;
            case NOT_FOUND -> MessageKey.EDITOR_APPLY_CODE_UNKNOWN;
            case UNEXPECTED -> MessageKey.EDITOR_FAILED_UNEXPECTED;
        };
    }

    private void done(final EditorService.Applied.Done done, final Locale locale,
            final List<Component> lines) {
        for (final EditorService.Change change : done.changes()) {
            final TagResolver where = this.where(change.scope(), locale);
            lines.add(switch (change) {
                case EditorService.Change.SettingSet set when SettingsSchema.secret(set.key()) ->
                        this.line(MessageKey.EDITOR_APPLY_SETTING_SET_SECRET, locale, where,
                                Placeholder.unparsed("key", set.key()));
                case EditorService.Change.SettingReset reset
                        when SettingsSchema.secret(reset.key()) ->
                        this.line(MessageKey.EDITOR_APPLY_SETTING_RESET_SECRET, locale, where,
                                Placeholder.unparsed("key", reset.key()));
                case EditorService.Change.SettingPublished published
                        when SettingsSchema.secret(published.key()) ->
                        this.line(MessageKey.EDITOR_APPLY_SETTING_PUBLISHED_SECRET, locale,
                                Placeholder.unparsed("key", published.key()),
                                Placeholder.unparsed("server", published.server()));
                case EditorService.Change.SettingSet set -> this.line(
                        MessageKey.EDITOR_APPLY_SETTING_SET, locale, where,
                        Placeholder.unparsed("key", set.key()),
                        Placeholder.unparsed("before", shorten(set.before())),
                        Placeholder.unparsed("after", shorten(set.after())));
                case EditorService.Change.SettingReset reset -> this.line(
                        MessageKey.EDITOR_APPLY_SETTING_RESET, locale, where,
                        Placeholder.unparsed("key", reset.key()),
                        Placeholder.unparsed("value", shorten(reset.now())));
                case EditorService.Change.SettingPublished published -> this.line(
                        MessageKey.EDITOR_APPLY_SETTING_PUBLISHED, locale,
                        Placeholder.unparsed("key", published.key()),
                        Placeholder.unparsed("server", published.server()),
                        Placeholder.unparsed("before", shorten(published.before())),
                        Placeholder.unparsed("after", shorten(published.after())));
                case EditorService.Change.MessageSet set -> this.line(
                        MessageKey.EDITOR_APPLY_MESSAGE_SET, locale, where,
                        Placeholder.unparsed("locale", set.locale()),
                        Placeholder.unparsed("key", set.key()),
                        Placeholder.unparsed("text", shorten(set.text())));
                case EditorService.Change.MessageReset reset -> this.line(
                        MessageKey.EDITOR_APPLY_MESSAGE_RESET, locale, where,
                        Placeholder.unparsed("locale", reset.locale()),
                        Placeholder.unparsed("key", reset.key()));
                case EditorService.Change.TemplateCreated created -> this.line(
                        MessageKey.EDITOR_APPLY_TEMPLATE_CREATED, locale,
                        Placeholder.unparsed("name", created.target()));
                case EditorService.Change.TemplateUpdated updated -> this.line(
                        MessageKey.EDITOR_APPLY_TEMPLATE_UPDATED, locale,
                        Placeholder.unparsed("name", updated.target()),
                        Placeholder.component("fields", this.fields(updated.fields(), locale)));
                case EditorService.Change.TemplateDeleted deleted -> this.line(
                        MessageKey.EDITOR_APPLY_TEMPLATE_DELETED, locale,
                        Placeholder.unparsed("name", deleted.target()));
                case EditorService.Change.Unchanged unchanged -> this.line(
                        MessageKey.EDITOR_APPLY_UNCHANGED, locale, where,
                        Placeholder.unparsed("section", unchanged.section().id()),
                        Placeholder.unparsed("target", unchanged.target()));
                case EditorService.Change.Conflict conflict -> this.line(switch (conflict.why()) {
                    case CHANGED -> MessageKey.EDITOR_APPLY_CONFLICT;
                    case FORBIDDEN -> MessageKey.EDITOR_APPLY_FAILED_FORBIDDEN;
                    case REFUSED -> MessageKey.EDITOR_APPLY_FAILED;
                }, locale, where, Placeholder.unparsed("section", conflict.section().id()),
                        Placeholder.unparsed("target", conflict.target()),
                        Placeholder.unparsed("details", shorten(conflict.detail())));
            });
        }
        lines.add(this.line(MessageKey.EDITOR_APPLY_SUMMARY, locale,
                Placeholder.unparsed("applied", Integer.toString(done.applied())),
                Placeholder.unparsed("unchanged", Integer.toString(done.unchanged())),
                Placeholder.unparsed("conflicts", Integer.toString(done.conflicts()))));
        if (done.conflicts() > 0) {
            lines.add(this.line(MessageKey.EDITOR_APPLY_CONFLICT_HINT, locale));
        }
    }

    /** {@code <where>}: " in server:lobby" for a scope that isn't global, else nothing. */
    private TagResolver where(final String scope, final Locale locale) {
        return Placeholder.component("where", scope.isEmpty() || scope.equals("global")
                ? Component.empty() : this.line(MessageKey.EDITOR_APPLY_IN_SCOPE, locale,
                        Placeholder.unparsed("scope", scope)));
    }

    private Component fields(final List<EditorService.FieldChange> fields, final Locale locale) {
        final List<Component> parts = new ArrayList<>();
        for (final EditorService.FieldChange field : fields) {
            parts.add(field.before() != null && field.after() != null
                    ? this.line(MessageKey.EDITOR_APPLY_TEMPLATE_FIELD_VALUES, locale,
                            Placeholder.unparsed("field", field.field()),
                            Placeholder.unparsed("before", shorten(field.before())),
                            Placeholder.unparsed("after", shorten(field.after())))
                    : this.line(MessageKey.EDITOR_APPLY_TEMPLATE_FIELD, locale,
                            Placeholder.unparsed("field", field.field())));
        }
        return Component.join(JoinConfiguration.commas(true), parts);
    }

    /** Values as short text, on one line, cut at {@link #MAX_VALUE}. */
    public static String shorten(final @Nullable Object value) {
        final String line = EditorService.text(value).replace("\r", "").replace('\n', ' ');
        return line.length() <= MAX_VALUE ? line
                : line.substring(0, MAX_VALUE - 1).stripTrailing() + "…";
    }
}
