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
