package net.serlith.sakurabans.core;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public final class RecordingLogger {

    public record Entry(String level, Object[] arguments) {

        public String format() {
            if (this.arguments.length == 0) {
                return "";
            }
            String text = String.valueOf(this.arguments[0]);
            for (int i = 1; i < this.arguments.length; i++) {
                text = text.replaceFirst("\\{}", Matcher.quoteReplacement(
                        String.valueOf(this.arguments[i])));
            }
            return text;
        }
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final ComponentLogger logger = (ComponentLogger) Proxy.newProxyInstance(
            ComponentLogger.class.getClassLoader(), new Class<?>[] {ComponentLogger.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "trace", "debug", "info", "warn", "error" -> this.entries.add(
                            new Entry(method.getName(), args == null ? new Object[0] : args));
                    case "getName" -> {
                        return "test";
                    }
                    default -> {
                        // Level checks and the rest: answer below.
                    }
                }
                return method.getReturnType() == boolean.class ? Boolean.TRUE : null;
            });

    public ComponentLogger logger() {
        return this.logger;
    }

    public List<Entry> entries(final String level) {
        return this.entries.stream().filter(entry -> entry.level().equals(level)).toList();
    }
}
