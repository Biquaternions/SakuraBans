package net.serlith.sakurabans.core.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.model.TemplateDisplays;
import net.serlith.sakurabans.core.model.TemplateKind;
import net.serlith.sakurabans.core.model.TemplateSteps;
import net.serlith.sakurabans.core.util.Durations;
import net.serlith.sakurabans.core.util.Scopes;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class TemplateFiles {

    public static final String FILE = "templates.yml";
    private static final String EXPORTS = "exports";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final Set<String> KEYS = Set.of("type", "ip", "shadow", "scope", "reason",
            "duration", "steps", "expiry", "address-shared", "permission-required", "actions",
            "groups", "message", "display");
    private static final Set<String> STEP_KEYS = Set.of("step", "reason", "message", "actions");
    // Keys of the format before D37, with what replaced them.
    private static final Map<String, String> REPLACED = Map.of(
            "ladder", "use 'type: ladder' and 'steps' (each step names its type: warn, kick,"
                    + " mute 1h, ban 7d, none)",
            "ladder-expiry", "use 'expiry'",
            "step-actions", "use 'actions' inside the step ({step: ..., actions: [...]})",
            "step-messages", "use 'message' inside the step ({step: ..., message: ...})");

    private final Path dataDirectory;

    public TemplateFiles(final Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public record Read(List<TemplateInfo> templates, List<ConfigIssue> errors) {
    }

    public Read read() {
        final ConfigurationNode root;
        try {
            final Path file = ConfigFiles.ensureDefault(this.dataDirectory, FILE);
            root = ConfigFiles.loader(file).load();
        } catch (final IOException e) {
            return new Read(List.of(), List.of(ConfigIssue.error(FILE, "", describe(e))));
        }
        return parse(root);
    }

    static Read parse(final ConfigurationNode root) {
        final List<ConfigIssue> errors = new ArrayList<>();
        final List<TemplateInfo> templates = new ArrayList<>();
        final ConfigurationNode section = root.node("templates");
        if (!section.virtual() && !section.isMap()) {
            errors.add(ConfigIssue.error(FILE, "templates", "must be a section"));
            return new Read(List.of(), errors);
        }
        for (final Map.Entry<Object, ? extends ConfigurationNode> entry
                : section.childrenMap().entrySet()) {
            final String name = String.valueOf(entry.getKey());
            final Optional<TemplateInfo> parsed =
                    template(name, entry.getValue(), "templates." + name, null, errors);
            parsed.ifPresent(templates::add);
        }
        // Variants (S62): {<scope>: {<name>: definition}}, each a whole definition.
        final ConfigurationNode variants = root.node("variants");
        if (!variants.virtual() && !variants.isMap()) {
            errors.add(ConfigIssue.error(FILE, "variants", "must be a section"));
            return new Read(List.copyOf(templates), List.copyOf(errors));
        }
        for (final Map.Entry<Object, ? extends ConfigurationNode> scoped
                : variants.childrenMap().entrySet()) {
            final String text = String.valueOf(scoped.getKey());
            final String path = "variants." + text;
            final Scope scope = Scopes.parse(text).orElse(null);
            if (scope == null || scope instanceof GlobalScope) {
                errors.add(ConfigIssue.error(FILE, path, "\"" + text
                        + "\" is not a server or category scope (server:<name> or"
                        + " category:<name>)"));
                continue;
            }
            if (!scoped.getValue().isMap()) {
                errors.add(ConfigIssue.error(FILE, path, "must be a section"));
                continue;
            }
            for (final Map.Entry<Object, ? extends ConfigurationNode> entry
                    : scoped.getValue().childrenMap().entrySet()) {
                final String name = String.valueOf(entry.getKey());
                template(name, entry.getValue(), path + "." + name, scope, errors)
                        .ifPresent(templates::add);
            }
        }
        return new Read(List.copyOf(templates), List.copyOf(errors));
    }

    /**
     * Reads one definition from a tree of maps, lists and scalars (what JSON or YAML gives), with
     * the same rules as the file. Errors have {@code path} in front. {@code variant}: the
     * variant's scope, null for the global definition.
     */
    public static Read definition(final String name, final @Nullable Object tree,
            final String path, final @Nullable Scope variant) {
        final List<ConfigIssue> errors = new ArrayList<>();
        final ConfigurationNode node = ConfigFiles.builder().build().createNode();
        try {
            node.set(tree);
        } catch (final SerializationException e) {
            return new Read(List.of(), List.of(ConfigIssue.error(FILE, path, describe(e))));
        }
        final Optional<TemplateInfo> parsed = template(name, node, path, variant, errors);
        return new Read(parsed.stream().toList(), List.copyOf(errors));
    }

    private static Optional<TemplateInfo> template(final String name,
            final ConfigurationNode node, final String path, final @Nullable Scope variant,
            final List<ConfigIssue> errors) {
        if (!node.isMap()) {
            errors.add(ConfigIssue.error(FILE, path, "must be a section"));
            return Optional.empty();
        }
        final int before = errors.size();
        for (final Object key : node.childrenMap().keySet()) {
            final String text = String.valueOf(key);
            if (REPLACED.containsKey(text)) {
                errors.add(ConfigIssue.error(FILE, path + "." + key, "is no longer read: "
                        + REPLACED.get(text)));
            } else if (!KEYS.contains(text)) {
                errors.add(ConfigIssue.error(FILE, path + "." + key, "unknown key"));
            }
        }
        final TemplateKind kind = kind(node.node("type"), path, errors);
        final Scope scope = scope(node.node("scope"), path, errors);
        final List<String> actions = strings(node.node("actions"), path + ".actions", errors);
        final TemplateDuration duration = presetDuration(node.node("duration"), path, errors);
        final List<TemplateStep> steps = steps(node.node("steps"), path + ".steps", errors);
        final String message = text(node.node("message"), path + ".message", errors);
        final TemplateDisplay display = display(node.node("display"), path, errors);
        final Duration expiry = expiry(node.node("expiry"), path, errors);
        final Map<String, Integer> groups = new LinkedHashMap<>();
        final ConfigurationNode groupsNode = node.node("groups");
        if (!groupsNode.virtual() && !groupsNode.isMap()) {
            errors.add(ConfigIssue.error(FILE, path + ".groups",
                    "must be a section of group: weight"));
        }
        groupsNode.childrenMap().forEach((key, value) -> {
            final Object raw = value.raw();
            if (raw instanceof Number number) {
                groups.put(String.valueOf(key), number.intValue());
            } else {
                errors.add(ConfigIssue.error(FILE, path + ".groups." + key,
                        "must be a weight (a number)"));
            }
        });
        final Boolean ip = flag(node.node("ip"), path, errors);
        final Boolean shadow = flag(node.node("shadow"), path, errors);
        final Boolean addressShared = flag(node.node("address-shared"), path, errors);
        final Boolean permissionRequired = flag(node.node("permission-required"), path, errors);
        if (errors.size() > before || kind == null) {
            return Optional.empty();
        }
        final TemplateBuilder builder = TemplateBuilder.of(name, kind)
                .ip(Boolean.TRUE.equals(ip))
                .shadow(Boolean.TRUE.equals(shadow))
                .scope(scope)
                .reason(node.node("reason").getString(""))
                .duration(duration)
                .steps(steps)
                .expiry(expiry)
                .addressShared(Boolean.TRUE.equals(addressShared))
                .permissionRequired(Boolean.TRUE.equals(permissionRequired))
                .actions(actions)
                .groups(groups)
                .message(message)
                .display(display)
                .variant(variant);
        final List<FieldError> problems = builder.validate();
        if (!problems.isEmpty()) {
            report(errors, path, problems);
            return Optional.empty();
        }
        return Optional.of(builder.build());
    }

    private static @Nullable TemplateKind kind(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        final String text = node.getString("").strip();
        final Optional<TemplateKind> kind = TemplateKind.parse(text);
        if (kind.isEmpty()) {
            errors.add(ConfigIssue.error(FILE, path + ".type", text.isEmpty() ? "is required"
                    : "\"" + text + "\" is not a type (ban, mute, warn, kick or ladder)"));
        }
        return kind.orElse(null);
    }

    private static TemplateDuration presetDuration(final ConfigurationNode node,
            final String path, final List<ConfigIssue> errors) {
        if (node.virtual()) {
            return TemplateDuration.open();
        }
        final String text = node.getString("");
        final Optional<TemplateDuration> duration = TemplateSteps.duration(text);
        if (duration.isEmpty()) {
            errors.add(ConfigIssue.error(FILE, path + ".duration",
                    TemplateSteps.durationError(text)));
        }
        return duration.orElse(TemplateDuration.open());
    }

    private static List<TemplateStep> steps(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        if (node.virtual()) {
            return List.of();
        }
        if (!node.isList()) {
            errors.add(ConfigIssue.error(FILE, path, "must be a list"));
            return List.of();
        }
        final List<TemplateStep> steps = new ArrayList<>();
        final List<? extends ConfigurationNode> children = node.childrenList();
        for (int i = 0; i < children.size(); i++) {
            step(children.get(i), path + "[" + i + "]", errors).ifPresent(steps::add);
        }
        return steps;
    }

    /** A step: its text, or a map of {@code step} (the text), reason, message and actions. */
    private static Optional<TemplateStep> step(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        final int before = errors.size();
        final ConfigurationNode textNode;
        String reason = null;
        String message = null;
        List<String> actions = List.of();
        if (node.isMap()) {
            for (final Object key : node.childrenMap().keySet()) {
                if (!STEP_KEYS.contains(String.valueOf(key))) {
                    errors.add(ConfigIssue.error(FILE, path + "." + key, "unknown key"));
                }
            }
            textNode = node.node("step");
            if (textNode.virtual()) {
                errors.add(ConfigIssue.error(FILE, path + ".step", "is required"));
            }
            reason = text(node.node("reason"), path + ".reason", errors);
            message = text(node.node("message"), path + ".message", errors);
            actions = strings(node.node("actions"), path + ".actions", errors);
        } else {
            textNode = node;
        }
        final String text = text(textNode, path + (node.isMap() ? ".step" : ""), errors);
        if (errors.size() > before || text == null) {
            return Optional.empty();
        }
        final TemplateSteps.Parsed parsed = TemplateSteps.parse(text);
        if (parsed.error() != null) {
            errors.add(ConfigIssue.error(FILE, path + (node.isMap() ? ".step" : ""),
                    parsed.error()));
            return Optional.empty();
        }
        try {
            return Optional.of(TemplateStep.of(parsed.type(), parsed.duration(), reason, message,
                    actions));
        } catch (final ValidationException e) {
            report(errors, path, e.errors());
            return Optional.empty();
        }
    }

    private static @Nullable String text(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        if (node.virtual()) {
            return null;
        }
        if (node.isMap() || node.isList() || node.raw() == null) {
            errors.add(ConfigIssue.error(FILE, path, "must be a text"));
            return null;
        }
        return String.valueOf(node.raw());
    }

    // No "local": a template is shared by every server, so "this server" means nothing.
    private static @Nullable Scope scope(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        if (node.virtual()) {
            return null;
        }
        final String text = node.getString("");
        final Optional<Scope> scope = Scopes.parse(text);
        if (scope.isEmpty()) {
            errors.add(ConfigIssue.error(FILE, path + ".scope", "\"" + text
                    + "\" is not a scope (global, server:<name> or category:<name>)"));
        }
        return scope.orElse(null);
    }

    private static TemplateDisplay display(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        final String text = text(node, path + ".display", errors);
        if (text == null) {
            return TemplateDisplay.CHAT;
        }
        final Optional<TemplateDisplay> display = TemplateDisplays.parse(text);
        if (display.isEmpty()) {
            errors.add(ConfigIssue.error(FILE, path + ".display",
                    "\"" + text + "\" is not a display (chat or dialog)"));
        }
        return display.orElse(TemplateDisplay.CHAT);
    }

    private static @Nullable Duration expiry(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        final String text = node.getString("");
        if (text.isBlank() || text.equalsIgnoreCase("none")) {
            return null;
        }
        final Optional<Duration> parsed = Durations.parse(text);
        if (parsed.isEmpty()) {
            errors.add(ConfigIssue.error(FILE, path + ".expiry",
                    "\"" + text + "\" is not a duration or 'none'"));
        }
        return parsed.orElse(null);
    }

    private static @Nullable Boolean flag(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        if (node.virtual()) {
            return false;
        }
        if (node.raw() instanceof Boolean value) {
            return value;
        }
        errors.add(ConfigIssue.error(FILE, path + "." + node.key(), "must be true or false"));
        return null;
    }

    private static List<String> strings(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        if (node.virtual()) {
            return List.of();
        }
        if (!node.isList()) {
            errors.add(ConfigIssue.error(FILE, path, "must be a list"));
            return List.of();
        }
        final List<String> values = new ArrayList<>();
        for (final ConfigurationNode child : node.childrenList()) {
            if (child.isMap() || child.isList() || child.raw() == null) {
                errors.add(ConfigIssue.error(FILE, path, "must be a list of texts"));
                return List.of();
            }
            values.add(String.valueOf(child.raw()));
        }
        return values;
    }

    private static void report(final List<ConfigIssue> errors, final String path,
            final List<FieldError> problems) {
        problems.forEach(problem -> errors.add(ConfigIssue.error(FILE, path + "."
                + problem.path(), problem.message())));
    }

    /** Writes global definitions under {@code templates} and variants under {@code variants}. */
    public Path export(final List<? extends TemplateInfo> templates, final Instant at)
            throws IOException {
        final Path directory = this.dataDirectory.resolve(EXPORTS);
        Files.createDirectories(directory);
        Path file = directory.resolve("templates-" + STAMP.format(at) + ".yml");
        for (int i = 2; Files.exists(file); i++) {
            file = directory.resolve("templates-" + STAMP.format(at) + "-" + i + ".yml");
        }
        final YamlConfigurationLoader loader = ConfigFiles.loader(file);
        final ConfigurationNode root = loader.createNode();
        for (final TemplateInfo template : templates) {
            final Optional<Scope> variant = template.variant();
            if (variant.isEmpty()) {
                root.node("templates", template.name()).set(tree(template));
            } else {
                root.node("variants", Scopes.format(variant.get()), template.name())
                        .set(tree(template));
            }
        }
        loader.save(root);
        return file;
    }

    /**
     * A template as {@code templates.yml} writes it, keys in file order. Optional keys at their
     * default are left out; the editor's JSON uses the same tree.
     */
    public static Map<String, Object> tree(final TemplateInfo template) {
        final Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("type", TemplateKind.of(template).key());
        if (template.ip()) {
            tree.put("ip", true);
        }
        if (template.shadow()) {
            tree.put("shadow", true);
        }
        template.scope().ifPresent(scope -> tree.put("scope", Scopes.format(scope)));
        tree.put("reason", template.reason());
        switch (template) {
            case PresetTemplate preset -> TemplateSteps.format(preset.duration())
                    .ifPresent(text -> tree.put("duration", text));
            case LadderTemplate ladder -> {
                tree.put("steps", ladder.steps().stream().map(TemplateFiles::stepTree)
                        .toList());
                ladder.expiry().ifPresent(expiry -> tree.put("expiry",
                        TemplateSteps.compact(expiry)));
                if (ladder.addressShared()) {
                    tree.put("address-shared", true);
                }
            }
        }
        if (template.permissionRequired()) {
            tree.put("permission-required", true);
        }
        if (!template.actions().isEmpty()) {
            tree.put("actions", List.copyOf(template.actions()));
        }
        if (!template.groups().isEmpty()) {
            tree.put("groups", new TreeMap<>(template.groups()));
        }
        template.message().ifPresent(text -> tree.put("message", text));
        if (template.display() != TemplateDisplay.CHAT) {
            tree.put("display", TemplateDisplays.key(template.display()));
        }
        return tree;
    }

    /** A step's text, or a map when it has a reason, a message or actions. */
    private static Object stepTree(final TemplateStep step) {
        final String text = TemplateSteps.format(step);
        if (step.reason().isEmpty() && step.message().isEmpty() && step.actions().isEmpty()) {
            return text;
        }
        final Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("step", text);
        step.reason().ifPresent(reason -> tree.put("reason", reason));
        step.message().ifPresent(message -> tree.put("message", message));
        if (!step.actions().isEmpty()) {
            tree.put("actions", List.copyOf(step.actions()));
        }
        return tree;
    }

    private static String describe(final Exception e) {
        final String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.strip();
    }

}
