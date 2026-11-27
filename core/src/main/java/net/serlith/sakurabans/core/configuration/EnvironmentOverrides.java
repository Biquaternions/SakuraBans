package net.serlith.sakurabans.core.configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.leangen.geantyref.GenericTypeReflector;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.serlith.sakurabans.core.configuration.file.StorageConfig;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.FieldData;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.serialize.SerializationException;

public final class EnvironmentOverrides {

    public static final String PREFIX = "SAKURABANS_";
    public static final String JSON_VARIABLE = PREFIX + "CONFIG";
    public static final String SOURCE = "environment";

    private enum Kind { SCALAR, LIST, MAP }

    private record Key(String file, String path, String key, String variable, Kind kind,
            boolean secret) {
    }

    public record Applied(String source, String key, boolean secret, @Nullable Object value) {
    }

    private record Value(Key key, String source, @Nullable Object value) {
    }

    private static final List<Key> KEYS = keys();

    private final Map<String, Value> values;
    private final List<ConfigIssue> errors;

    public static final EnvironmentOverrides NONE =
            new EnvironmentOverrides(Map.of(), List.of());

    private EnvironmentOverrides(final Map<String, Value> values,
            final List<ConfigIssue> errors) {
        this.values = values;
        this.errors = errors;
    }

    public static EnvironmentOverrides of(final Map<String, String> environment) {
        final Map<String, Value> values = new LinkedHashMap<>();
        final List<ConfigIssue> errors = new ArrayList<>();
        final String json = environment.get(JSON_VARIABLE);
        if (json != null) {
            readJson(json, values, errors);
        }
        // Sorted, so the errors come in a stable order.
        for (final String name : new TreeMap<>(environment).keySet()) {
            if (!name.startsWith(PREFIX) || name.equals(JSON_VARIABLE)) {
                continue;
            }
            final Key key = KEYS.stream().filter(candidate -> candidate.variable().equals(name))
                    .findFirst().orElse(null);
            if (key == null) {
                errors.add(ConfigIssue.error(SOURCE, name, refusal(name, "")));
                continue;
            }
            final String text = environment.get(name);
            try {
                values.put(key.key(), new Value(key, name, fromText(key, text)));
            } catch (final IllegalArgumentException e) {
                errors.add(ConfigIssue.error(SOURCE, name, e.getMessage()));
            }
        }
        return new EnvironmentOverrides(Map.copyOf(values), List.copyOf(errors));
    }

    public List<ConfigIssue> errors() {
        return this.errors;
    }

    public boolean empty() {
        return this.values.isEmpty();
    }

    public boolean supplies(final String file, final String path) {
        return this.values.values().stream().anyMatch(value ->
                value.key().file().equals(file) && value.key().path().equals(path));
    }

    public List<Applied> applied() {
        return this.values.values().stream()
                .sorted((first, second) -> first.key().key().compareTo(second.key().key()))
                .map(value -> new Applied(value.source(), value.key().key(), value.key().secret(),
                        value.key().secret() ? null : value.value()))
                .toList();
    }

    public void apply(final String file, final ConfigurationNode root) {
        for (final Value value : this.values.values()) {
            if (value.key().file().equals(file)) {
                root.node((Object[]) value.key().path().split("\\.")).raw(value.value());
            }
        }
    }

    public ConfigIssue label(final ConfigIssue issue) {
        for (final Value value : this.values.values()) {
            final Key key = value.key();
            if (!key.file().equals(issue.file())) {
                continue;
            }
            final String path = issue.path();
            if (path.equals(key.path()) || path.startsWith(key.path() + ".")
                    || path.startsWith(key.path() + "[")) {
                String message = issue.message();
                if (key.secret()) {
                    for (final String text : texts(value.value())) {
                        if (text.length() >= 4) {
                            message = message.replace(text, "<hidden>");
                        }
                    }
                }
                return new ConfigIssue(SOURCE, value.source() + path.substring(key.path().length()),
                        message + " (from the environment)", issue.severity());
            }
        }
        return issue;
    }

    public static Optional<String> variableFor(final String file, final String path) {
        return KEYS.stream().filter(key -> key.file().equals(file) && key.path().equals(path))
                .map(Key::variable).findFirst();
    }

    public static Map<String, String> variables() {
        final Map<String, String> all = new LinkedHashMap<>();
        KEYS.forEach(key -> all.put(key.variable(), key.key()));
        return all;
    }

    private static List<String> texts(final @Nullable Object value) {
        final List<String> texts = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(item -> texts.addAll(texts(item)));
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> texts.addAll(texts(item)));
        } else if (value != null) {
            texts.add(String.valueOf(value));
        }
        return texts;
    }

    // The keys the environment may set.

    private static List<Key> keys() {
        final List<Key> keys = new ArrayList<>();
        keys.add(new Key(ConfigFiles.CONFIG, "server.name", "server.name",
                nameOf(ConfigFiles.CONFIG, "server.name"), Kind.SCALAR, false));
        keys.add(new Key(ConfigFiles.CONFIG, "server.categories", "server.categories",
                nameOf(ConfigFiles.CONFIG, "server.categories"), Kind.LIST, false));
        keys.add(new Key(ConfigFiles.CONFIG, "web.token", "web.token",
                nameOf(ConfigFiles.CONFIG, "web.token"), Kind.SCALAR, true));
        for (final String path : List.of("geoip.enabled", "geoip.account-id",
                "geoip.license-key", "geoip.refresh")) {
            keys.add(new Key(ConfigFiles.CONFIG, path, path, nameOf(ConfigFiles.CONFIG, path),
                    Kind.SCALAR, SettingsSchema.secret(path)));
        }
        storageKeys(StorageConfig.class, "", keys);
        return List.copyOf(keys);
    }

    private static void storageKeys(final Type model, final String prefix, final List<Key> keys) {
        final ObjectMapper<?> mapper;
        try {
            mapper = ObjectMapper.factory().get(model);
        } catch (final SerializationException e) {
            throw new IllegalStateException(e);
        }
        final ConfigurationNode empty = ConfigFiles.builder().build().createNode();
        for (final FieldData<?, ?> field : mapper.fields()) {
            final ConfigurationNode child = field.resolveNode(empty);
            if (child == null) {
                continue;
            }
            final String path = prefix + child.key();
            final Class<?> raw = GenericTypeReflector.erase(field.resolvedType().getType());
            if (raw.isAnnotationPresent(ConfigSerializable.class)) {
                storageKeys(field.resolvedType().getType(), path + ".", keys);
            } else {
                final Kind kind = Map.class.isAssignableFrom(raw) ? Kind.MAP
                        : List.class.isAssignableFrom(raw) || Set.class.isAssignableFrom(raw)
                                ? Kind.LIST : Kind.SCALAR;
                keys.add(new Key(ConfigFiles.STORAGE, path, "storage." + path,
                        nameOf(ConfigFiles.STORAGE, path), kind,
                        kind == Kind.MAP || path.equals("password")
                                || path.equals("address-hash-key")));
            }
        }
    }

    private static String nameOf(final String file, final String path) {
        return PREFIX + (file.equals(ConfigFiles.STORAGE) ? "STORAGE_" : "")
                + path.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    // Reading values.

    private static @Nullable Object fromText(final Key key, final String text) {
        return switch (key.kind()) {
            case SCALAR -> text;
            case LIST -> text.stripLeading().startsWith("[")
                    ? fromJson(key, parse(text))
                    : List.of(text.split(",")).stream().map(String::strip)
                            .filter(item -> !item.isEmpty()).map(item -> (Object) item).toList();
            case MAP -> {
                final JsonElement element = parse(text);
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("must be a JSON object");
                }
                yield fromJson(key, element);
            }
        };
    }

    private static JsonElement parse(final String text) {
        try {
            return JsonParser.parseString(text);
        } catch (final JsonParseException e) {
            throw new IllegalArgumentException("isn't valid JSON: " + firstLine(e.getMessage()));
        }
    }

    private static String firstLine(final @Nullable String message) {
        return message == null ? "" : message.lines().findFirst().orElse("");
    }

    private static void readJson(final String text, final Map<String, Value> values,
            final List<ConfigIssue> errors) {
        final JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (final JsonParseException e) {
            errors.add(ConfigIssue.error(SOURCE, JSON_VARIABLE,
                    "isn't valid JSON: " + firstLine(e.getMessage())));
            return;
        }
        if (!root.isJsonObject()) {
            errors.add(ConfigIssue.error(SOURCE, JSON_VARIABLE, "must be one JSON object"));
            return;
        }
        walk(root.getAsJsonObject(), "", values, errors);
    }

    private static void walk(final JsonObject object, final String prefix,
            final Map<String, Value> values, final List<ConfigIssue> errors) {
        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            final String name = prefix + entry.getKey();
            final Key key = KEYS.stream().filter(candidate -> candidate.key().equals(name))
                    .findFirst().orElse(null);
            final String source = JSON_VARIABLE + "[" + name + "]";
            if (key != null) {
                try {
                    // The individual variables win: they are put after these.
                    values.put(key.key(), new Value(key, source,
                            key.kind() == Kind.LIST && entry.getValue().isJsonPrimitive()
                                    ? fromText(key, entry.getValue().getAsString())
                                    : fromJson(key, entry.getValue())));
                } catch (final IllegalArgumentException e) {
                    errors.add(ConfigIssue.error(SOURCE, source, e.getMessage()));
                }
            } else if (entry.getValue().isJsonObject()
                    && !entry.getValue().getAsJsonObject().isEmpty()) {
                // A section, or something that isn't a setting: its leaves say which.
                walk(entry.getValue().getAsJsonObject(), name + ".", values, errors);
            } else {
                errors.add(ConfigIssue.error(SOURCE, source, refusal(dottedName(name), name)));
            }
        }
    }

    private static String dottedName(final String dotted) {
        return PREFIX + dotted.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    private static @Nullable Object fromJson(final Key key, final JsonElement element) {
        if (element.isJsonNull()) {
            throw new IllegalArgumentException("must not be null");
        }
        return switch (key.kind()) {
            case SCALAR -> {
                if (!element.isJsonPrimitive()) {
                    throw new IllegalArgumentException("must be a single value");
                }
                yield primitive(element.getAsJsonPrimitive());
            }
            case LIST -> {
                if (!element.isJsonArray()) {
                    throw new IllegalArgumentException("must be a list");
                }
                final List<Object> items = new ArrayList<>();
                for (final JsonElement item : (JsonArray) element) {
                    if (!item.isJsonPrimitive()) {
                        throw new IllegalArgumentException("must be a list of single values");
                    }
                    items.add(primitive(item.getAsJsonPrimitive()));
                }
                yield items;
            }
            case MAP -> {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("must be a JSON object");
                }
                final Map<String, Object> map = new LinkedHashMap<>();
                for (final Map.Entry<String, JsonElement> entry
                        : element.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonPrimitive()) {
                        throw new IllegalArgumentException("must map names to single values");
                    }
                    map.put(entry.getKey(), String.valueOf(primitive(
                            entry.getValue().getAsJsonPrimitive())));
                }
                yield map;
            }
        };
    }

    private static Object primitive(final JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            final BigDecimal number = primitive.getAsBigDecimal();
            try {
                return number.scale() <= 0 ? (Object) number.longValueExact()
                        : (Object) number.doubleValue();
            } catch (final ArithmeticException e) {
                return number.toPlainString();
            }
        }
        return primitive.getAsString();
    }

    private static String refusal(final String variable, final String dotted) {
        for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            final String path = SettingsSchema.path(entry.key());
            final boolean named = dotted.isEmpty()
                    ? dottedName(entry.key().replace(':', '.')).equals(variable)
                    : entry.key().replace(':', '.').equals(dotted);
            if (!named) {
                continue;
            }
            return entry.editable()
                    ? entry.key() + " is a " + entry.level().name().toLowerCase(Locale.ROOT)
                            + " setting and lives in the database, not in the environment:"
                            + " change it with /sb settings or in the web editor"
                    : path + " can only be set in " + SettingsSchema.file(entry.key())
                            + " (the environment sets server.name, server.categories, web.token,"
                            + " geoip.enabled, geoip.account-id, geoip.license-key, geoip.refresh"
                            + " and the keys of storage.yml)";
        }
        return "isn't a setting the environment can set (it sets server.name,"
                + " server.categories, web.token, geoip.enabled, geoip.account-id,"
                + " geoip.license-key, geoip.refresh and the keys of storage.yml; see the"
                + " documentation for the variable names)";
    }
}
