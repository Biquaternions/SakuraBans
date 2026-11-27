package net.serlith.sakurabans.core.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.serlith.sakurabans.api.validation.FieldError;
import org.jspecify.annotations.Nullable;

/** What the browser sends back (protocol section 6), in the order it was sent. */
public record EditorChanges(int format, long sequence, List<SettingsChange> settings,
        List<LocaleChange> messages, List<TemplateChange> templates) {

    public EditorChanges {
        settings = List.copyOf(settings);
        messages = List.copyOf(messages);
        templates = List.copyOf(templates);
    }

    /** One scope's block; {@code set} values are plain trees, as JSON gave them. */
    public record SettingsChange(String scope, long expectedVersion,
            Map<String, @Nullable Object> set, List<String> reset, @Nullable Publish publish) {

        public SettingsChange {
            set = Collections.unmodifiableMap(new LinkedHashMap<>(set));
            reset = List.copyOf(reset);
        }
    }

    /** Make a server's file values (by its name) the global overrides of these keys. */
    public record Publish(String server, List<String> keys) {

        public Publish {
            keys = List.copyOf(keys);
        }
    }

    public record LocaleChange(String scope, String locale, long expectedVersion,
            Map<String, String> set, List<String> reset) {

        public LocaleChange {
            set = Collections.unmodifiableMap(new LinkedHashMap<>(set));
            reset = List.copyOf(reset);
        }
    }

    /**
     * {@code scope}: the definition's scope as written, {@code global} or a variant's server
     * or category (S62).
     */
    public sealed interface TemplateChange {

        String scope();

        String name();

        record Create(String scope, String name, Map<String, @Nullable Object> definition)
                implements TemplateChange {
        }

        record Update(String scope, String name, long expectedVersion,
                Map<String, @Nullable Object> definition) implements TemplateChange {
        }

        record Delete(String scope, String name, long expectedVersion)
                implements TemplateChange {
        }
    }

    public sealed interface Parsed {

        record Read(EditorChanges changes) implements Parsed {
        }

        /** Its {@code format} isn't the one the payload had. */
        record OtherFormat(int format) implements Parsed {
        }

        record Invalid(List<FieldError> errors) implements Parsed {

            public Invalid {
                errors = List.copyOf(errors);
            }
        }
    }

    public static Parsed parse(final String json) {
        final JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (final JsonParseException e) {
            return invalid("", "is not JSON");
        }
        if (!root.isJsonObject()) {
            return invalid("", "must be an object");
        }
        final JsonObject object = root.getAsJsonObject();
        final Reader reader = new Reader();
        final Long format = reader.number(object.get("format"), "format");
        if (format == null) {
            return new Parsed.Invalid(reader.errors);
        }
        if (format != EditorPayload.FORMAT) {
            return new Parsed.OtherFormat(format.intValue());
        }
        reader.known(object, "", List.of("format", "sequence", "settings", "messages",
                "templates"));
        final Long sequence = reader.number(object.get("sequence"), "sequence");
        final List<SettingsChange> settings = reader.settings(object.get("settings"));
        final List<LocaleChange> messages = reader.messages(object.get("messages"));
        final List<TemplateChange> templates = reader.templates(object.get("templates"));
        if (!reader.errors.isEmpty() || sequence == null) {
            return new Parsed.Invalid(reader.errors);
        }
        return new Parsed.Read(new EditorChanges(format.intValue(), sequence, settings, messages,
                templates));
    }

    private static Parsed invalid(final String path, final String message) {
        return new Parsed.Invalid(List.of(FieldError.of(path, FieldError.Code.INVALID,
                message)));
    }

    private static final class Reader {

        private final List<FieldError> errors = new ArrayList<>();

        private List<SettingsChange> settings(final @Nullable JsonElement element) {
            final List<SettingsChange> changes = new ArrayList<>();
            final JsonArray array = this.optionalArray(element, "settings");
            for (int i = 0; i < array.size(); i++) {
                final String path = "settings[" + i + "]";
                final JsonObject object = this.object(array.get(i), path);
                if (object == null) {
                    continue;
                }
                this.known(object, path, List.of("scope", "expectedVersion", "set", "reset",
                        "publish"));
                final String scope = this.string(object.get("scope"), path + ".scope");
                final Long version = this.number(object.get("expectedVersion"),
                        path + ".expectedVersion");
                final Map<String, @Nullable Object> set = new LinkedHashMap<>();
                this.optionalObject(object.get("set"), path + ".set").ifPresent(values ->
                        values.entrySet().forEach(entry ->
                                set.put(entry.getKey(), EditorJson.plain(entry.getValue()))));
                final List<String> reset = this.strings(object.get("reset"), path + ".reset");
                final Publish publish = this.optionalObject(object.get("publish"),
                        path + ".publish").map(value -> this.publish(value, path + ".publish"))
                        .orElse(null);
                if (scope != null && version != null) {
                    changes.add(new SettingsChange(scope, version, set, reset, publish));
                }
            }
            return changes;
        }

        private @Nullable Publish publish(final JsonObject object, final String path) {
            this.known(object, path, List.of("server", "keys"));
            final String server = this.string(object.get("server"), path + ".server");
            final List<String> keys = this.strings(object.get("keys"), path + ".keys");
            return server == null ? null : new Publish(server, keys);
        }

        private List<LocaleChange> messages(final @Nullable JsonElement element) {
            final List<LocaleChange> changes = new ArrayList<>();
            final JsonArray array = this.optionalArray(element, "messages");
            for (int i = 0; i < array.size(); i++) {
                final String path = "messages[" + i + "]";
                final JsonObject object = this.object(array.get(i), path);
                if (object == null) {
                    continue;
                }
                this.known(object, path, List.of("scope", "locale", "expectedVersion", "set",
                        "reset"));
                final String scope = this.string(object.get("scope"), path + ".scope");
                final String locale = this.string(object.get("locale"), path + ".locale");
                final Long version = this.number(object.get("expectedVersion"),
                        path + ".expectedVersion");
                final Map<String, String> set = new LinkedHashMap<>();
                this.optionalObject(object.get("set"), path + ".set").ifPresent(values ->
                        values.entrySet().forEach(entry -> {
                            final String text = this.string(entry.getValue(),
                                    path + ".set." + entry.getKey());
                            if (text != null) {
                                set.put(entry.getKey(), text);
                            }
                        }));
                final List<String> reset = this.strings(object.get("reset"), path + ".reset");
                if (scope != null && locale != null && version != null) {
                    changes.add(new LocaleChange(scope, locale, version, set, reset));
                }
            }
            return changes;
        }

        private List<TemplateChange> templates(final @Nullable JsonElement element) {
            final List<TemplateChange> changes = new ArrayList<>();
            final JsonArray array = this.optionalArray(element, "templates");
            for (int i = 0; i < array.size(); i++) {
                final String path = "templates[" + i + "]";
                final JsonObject object = this.object(array.get(i), path);
                if (object == null) {
                    continue;
                }
                final String op = this.string(object.get("op"), path + ".op");
                final String scope = this.string(object.get("scope"), path + ".scope");
                final String name = this.string(object.get("name"), path + ".name");
                if (op == null || name == null) {
                    continue;
                }
                switch (op) {
                    case "create" -> {
                        this.known(object, path, List.of("op", "scope", "name", "definition"));
                        final Map<String, @Nullable Object> definition =
                                this.definition(object.get("definition"), path);
                        if (scope != null && definition != null) {
                            changes.add(new TemplateChange.Create(scope, name, definition));
                        }
                    }
                    case "update" -> {
                        this.known(object, path, List.of("op", "scope", "name",
                                "expectedVersion", "definition"));
                        final Long version = this.number(object.get("expectedVersion"),
                                path + ".expectedVersion");
                        final Map<String, @Nullable Object> definition =
                                this.definition(object.get("definition"), path);
                        if (scope != null && version != null && definition != null) {
                            changes.add(new TemplateChange.Update(scope, name, version,
                                    definition));
                        }
                    }
                    case "delete" -> {
                        this.known(object, path, List.of("op", "scope", "name",
                                "expectedVersion"));
                        final Long version = this.number(object.get("expectedVersion"),
                                path + ".expectedVersion");
                        if (scope != null && version != null) {
                            changes.add(new TemplateChange.Delete(scope, name, version));
                        }
                    }
                    default -> this.error(path + ".op", "must be create, update or delete");
                }
            }
            return changes;
        }

        @SuppressWarnings("unchecked")
        private @Nullable Map<String, @Nullable Object> definition(
                final @Nullable JsonElement element, final String path) {
            final JsonObject object = this.object(element, path + ".definition");
            return object == null ? null
                    : (Map<String, @Nullable Object>) EditorJson.plain(object);
        }

        private void known(final JsonObject object, final String path,
                final List<String> keys) {
            for (final String key : object.keySet()) {
                if (!keys.contains(key)) {
                    this.error(path.isEmpty() ? key : path + "." + key, "is not a known key");
                }
            }
        }

        private @Nullable JsonObject object(final @Nullable JsonElement element,
                final String path) {
            if (element == null || !element.isJsonObject()) {
                this.error(path, "must be an object");
                return null;
            }
            return element.getAsJsonObject();
        }

        private Optional<JsonObject> optionalObject(final @Nullable JsonElement element,
                final String path) {
            if (element == null || element.isJsonNull()) {
                return Optional.empty();
            }
            return Optional.ofNullable(this.object(element, path));
        }

        private JsonArray optionalArray(final @Nullable JsonElement element, final String path) {
            if (element == null || element.isJsonNull()) {
                return new JsonArray();
            }
            if (!element.isJsonArray()) {
                this.error(path, "must be a list");
                return new JsonArray();
            }
            return element.getAsJsonArray();
        }

        private List<String> strings(final @Nullable JsonElement element, final String path) {
            final List<String> values = new ArrayList<>();
            final JsonArray array = this.optionalArray(element, path);
            for (int i = 0; i < array.size(); i++) {
                final String value = this.string(array.get(i), path + "[" + i + "]");
                if (value != null) {
                    values.add(value);
                }
            }
            return values;
        }

        private @Nullable String string(final @Nullable JsonElement element, final String path) {
            if (element instanceof JsonPrimitive primitive && primitive.isString()) {
                return primitive.getAsString();
            }
            this.error(path, "must be a text");
            return null;
        }

        private @Nullable Long number(final @Nullable JsonElement element, final String path) {
            if (element instanceof JsonPrimitive primitive && primitive.isNumber()
                    && EditorJson.plain(primitive) instanceof Long value && value >= 0) {
                return value;
            }
            this.error(path, "must be a whole number from 0");
            return null;
        }

        private void error(final String path, final String message) {
            this.errors.add(FieldError.of(path, FieldError.Code.INVALID, message));
        }
    }
}
