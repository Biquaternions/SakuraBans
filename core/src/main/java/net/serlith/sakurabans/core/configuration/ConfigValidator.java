package net.serlith.sakurabans.core.configuration;

import io.leangen.geantyref.GenericTypeReflector;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.FieldData;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.serialize.SerializationException;

final class ConfigValidator {

    private final String file;
    private final List<ConfigIssue> issues = new ArrayList<>();
    private final List<String> missing = new ArrayList<>();

    private ConfigValidator(final String file) {
        this.file = file;
    }

    record Result(List<ConfigIssue> issues, List<String> missingKeys) {

        boolean hasErrors() {
            return this.issues.stream()
                    .anyMatch(issue -> issue.severity() == ConfigIssue.Severity.ERROR);
        }
    }

    static Result validate(final String file, final ConfigurationNode root, final Type model) {
        final ConfigValidator validator = new ConfigValidator(file);
        validator.section(root, model, "");
        return new Result(List.copyOf(validator.issues), List.copyOf(validator.missing));
    }

    private void section(final ConfigurationNode node, final Type type, final String path) {
        if (!node.virtual() && !node.isMap() && !(node.isNull() && path.isEmpty())) {
            this.error(path, "must be a section, not \"" + node.raw() + "\"");
            return;
        }
        final ObjectMapper<?> mapper;
        try {
            mapper = ObjectMapper.factory().get(type);
        } catch (final SerializationException e) {
            throw new IllegalStateException("not a configuration model: " + type, e);
        }
        final Set<Object> known = new HashSet<>();
        for (final FieldData<?, ?> field : mapper.fields()) {
            final ConfigurationNode child = field.resolveNode(node);
            if (child == null) {
                continue;
            }
            known.add(child.key());
            final String childPath = join(path, String.valueOf(child.key()));
            if (child.virtual()) {
                this.missing.add(childPath);
                continue;
            }
            this.value(child, field.resolvedType().getType(), childPath);
        }
        for (final Object key : node.childrenMap().keySet()) {
            if (!known.contains(key)) {
                this.issues.add(ConfigIssue.warning(this.file, join(path, String.valueOf(key)),
                        "unknown key, ignored (a typo?)"));
            }
        }
    }

    private void value(final ConfigurationNode node, final Type type, final String path) {
        final Class<?> raw = GenericTypeReflector.erase(type);
        if (raw.isAnnotationPresent(ConfigSerializable.class)) {
            this.section(node, type, path);
        } else if (Map.class.isAssignableFrom(raw)) {
            if (!node.isMap() && !node.isNull()) {
                this.error(path, "must be a section, not \"" + node.raw() + "\"");
                return;
            }
            final Type valueType = typeArgument(type, 1);
            node.childrenMap().forEach((key, child) ->
                    this.value(child, valueType, join(path, String.valueOf(key))));
        } else if (List.class.isAssignableFrom(raw) || Set.class.isAssignableFrom(raw)) {
            if (!node.isList() && !node.isNull()) {
                this.error(path, "must be a list, not \"" + node.raw() + "\"");
                return;
            }
            final Type elementType = typeArgument(type, 0);
            final List<? extends ConfigurationNode> elements = node.childrenList();
            for (int i = 0; i < elements.size(); i++) {
                this.value(elements.get(i), elementType, path + "[" + i + "]");
            }
        } else {
            if (node.isMap() || node.isList()) {
                this.error(path, "must be a single value, not a section or list");
                return;
            }
            try {
                if (node.get(type) == null) {
                    this.error(path, "must not be empty");
                }
            } catch (final SerializationException e) {
                this.error(path, message(e, node.raw()));
            }
        }
    }

    private void error(final String path, final String message) {
        this.issues.add(ConfigIssue.error(this.file, path, message));
    }

    private static String message(final SerializationException e, final @Nullable Object raw) {
        final Throwable cause = e.getCause();
        if (cause instanceof NumberFormatException) {
            return "\"" + raw + "\" is not a number";
        }
        if (cause instanceof IllegalArgumentException && raw != null) {
            return "\"" + raw + "\" is not one of the allowed values";
        }
        final String text = e.rawMessage();
        return text == null || text.isBlank() ? "\"" + raw + "\" is invalid" : text;
    }

    private static Type typeArgument(final Type type, final int index) {
        return type instanceof ParameterizedType parameterized
                ? parameterized.getActualTypeArguments()[index]
                : Object.class;
    }

    private static String join(final String path, final String key) {
        return path.isEmpty() ? key : path + "." + key;
    }
}
