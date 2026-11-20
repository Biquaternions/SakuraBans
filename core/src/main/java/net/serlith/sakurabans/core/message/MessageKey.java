package net.serlith.sakurabans.core.message;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public record MessageKey(String key) {

    public static final MessageKey PREFIX = new MessageKey("prefix");

    public static final MessageKey ERROR_INTERNAL = new MessageKey("error.internal");
    public static final MessageKey ERROR_NOT_READY = new MessageKey("error.not_ready");
    public static final MessageKey ERROR_NO_PERMISSION = new MessageKey("error.no_permission");

    public static final MessageKey OPERATOR_CONSOLE = new MessageKey("operator.console");

    public static final MessageKey TYPE_BAN = new MessageKey("type.ban");
    public static final MessageKey TYPE_MUTE = new MessageKey("type.mute");
    public static final MessageKey TYPE_WARN = new MessageKey("type.warn");
    public static final MessageKey TYPE_KICK = new MessageKey("type.kick");

    public static final MessageKey SCOPE_GLOBAL = new MessageKey("scope.global");
    public static final MessageKey SCOPE_SERVER = new MessageKey("scope.server");
    public static final MessageKey SCOPE_CATEGORY = new MessageKey("scope.category");

    public static final MessageKey TIME_PERMANENT = new MessageKey("time.permanent");
    public static final MessageKey TIME_NEVER = new MessageKey("time.never");
    public static final MessageKey TIME_SEPARATOR = new MessageKey("time.separator");
    public static final MessageKey TIME_LAST_SEPARATOR = new MessageKey("time.last_separator");
    public static final MessageKey TIME_SECOND = new MessageKey("time.second");
    public static final MessageKey TIME_SECONDS = new MessageKey("time.seconds");
    public static final MessageKey TIME_MINUTE = new MessageKey("time.minute");
    public static final MessageKey TIME_MINUTES = new MessageKey("time.minutes");
    public static final MessageKey TIME_HOUR = new MessageKey("time.hour");
    public static final MessageKey TIME_HOURS = new MessageKey("time.hours");
    public static final MessageKey TIME_DAY = new MessageKey("time.day");
    public static final MessageKey TIME_DAYS = new MessageKey("time.days");
    public static final MessageKey TIME_YEAR = new MessageKey("time.year");
    public static final MessageKey TIME_YEARS = new MessageKey("time.years");

    public static final MessageKey SCREEN_BAN = new MessageKey("screen.ban");
    public static final MessageKey SCREEN_BAN_TEMPORARY = new MessageKey("screen.ban_temporary");
    public static final MessageKey SCREEN_KICK = new MessageKey("screen.kick");

    public static final MessageKey VICTIM_MUTED = new MessageKey("victim.muted");
    public static final MessageKey VICTIM_MUTED_TEMPORARY =
            new MessageKey("victim.muted_temporary");
    public static final MessageKey VICTIM_WARNED = new MessageKey("victim.warned");

    public static final MessageKey CHAT_MUTED = new MessageKey("chat.muted");
    public static final MessageKey CHAT_MUTED_TEMPORARY = new MessageKey("chat.muted_temporary");
    public static final MessageKey CHAT_COMMAND_MUTED = new MessageKey("chat.command_muted");
    public static final MessageKey CHAT_LOADING = new MessageKey("chat.loading");

    public static final MessageKey NOTIFY_SILENT_TAG = new MessageKey("notify.silent_tag");
    public static final MessageKey NOTIFY_BAN = new MessageKey("notify.ban");
    public static final MessageKey NOTIFY_MUTE = new MessageKey("notify.mute");
    public static final MessageKey NOTIFY_WARN = new MessageKey("notify.warn");
    public static final MessageKey NOTIFY_KICK = new MessageKey("notify.kick");
    public static final MessageKey NOTIFY_UNBAN = new MessageKey("notify.unban");
    public static final MessageKey NOTIFY_UNMUTE = new MessageKey("notify.unmute");
    public static final MessageKey NOTIFY_UNWARN = new MessageKey("notify.unwarn");
    public static final MessageKey NOTIFY_ATTEMPT_JOIN = new MessageKey("notify.attempt_join");
    public static final MessageKey NOTIFY_ATTEMPT_CHAT = new MessageKey("notify.attempt_chat");

    private static final List<MessageKey> ALL = Arrays.stream(MessageKey.class.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers())
                    && field.getType() == MessageKey.class)
            .map(MessageKey::read)
            .toList();

    public static List<MessageKey> all() {
        return ALL;
    }

    private static MessageKey read(final Field field) {
        try {
            return (MessageKey) field.get(null);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
