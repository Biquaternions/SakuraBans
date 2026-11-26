package net.serlith.sakurabans.core.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.model.TemplateDefinition;
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
            "ladder", "ladder-expiry", "address-shared", "permission-required", "actions",
            "step-actions", "groups", "message", "step-messages");

    private final Path dataDirectory;

    public TemplateFiles(final Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public record Read(List<TemplateDefinition> templates, List<ConfigIssue> errors) {
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
        final List<TemplateDefinition> templates = new ArrayList<>();
        final ConfigurationNode section = root.node("templates");
        if (!section.virtual() && !section.isMap()) {
            errors.add(ConfigIssue.error(FILE, "templates", "must be a section"));
            return new Read(List.of(), errors);
        }
        for (final Map.Entry<Object, ? extends ConfigurationNode> entry
                : section.childrenMap().entrySet()) {
            final String name = String.valueOf(entry.getKey());
            final Optional<TemplateDefinition> parsed =
                    template(name, entry.getValue(), "templates." + name, errors);
            parsed.ifPresent(templates::add);
        }
        return new Read(List.copyOf(templates), List.copyOf(errors));
    }

    /**
     * Reads one definition from a tree of maps, lists and scalars (what JSON or YAML gives), with
     * the same rules as the file. Errors have {@code path} in front.
     */
    public static Read definition(final String name, final @Nullable Object tree,
            final String path) {
        final List<ConfigIssue> errors = new ArrayList<>();
        final ConfigurationNode node = ConfigFiles.builder().build().createNode();
        try {
            node.set(tree);
        } catch (final SerializationException e) {
            return new Read(List.of(), List.of(ConfigIssue.error(FILE, path, describe(e))));
        }
        final Optional<TemplateDefinition> parsed = template(name, node, path, errors);
        return new Read(parsed.stream().toList(), List.copyOf(errors));
    }

    private static Optional<TemplateDefinition> template(final String name,
            final ConfigurationNode node, final String path, final List<ConfigIssue> errors) {
        if (!node.isMap()) {
            errors.add(ConfigIssue.error(FILE, path, "must be a section"));
            return Optional.empty();
        }
        final int before = errors.size();
        for (final Object key : node.childrenMap().keySet()) {
            if (!KEYS.contains(String.valueOf(key))) {
                errors.add(ConfigIssue.error(FILE, path + "." + key, "unknown key"));
            }
        }
        final PunishmentType type = type(node.node("type"), path, errors);
        final Scope scope = scope(node.node("scope"), path, errors);
        final List<String> actions = strings(node.node("actions"), path + ".actions", errors);
        final Map<Integer, List<String>> stepActions = new TreeMap<>();
        final ConfigurationNode stepActionsNode = node.node("step-actions");
        if (!stepActionsNode.virtual()) {
            if (!stepActionsNode.isMap()) {
                errors.add(ConfigIssue.error(FILE, path + ".step-actions", "must be a section"));
            }
            stepActionsNode.childrenMap().forEach((key, value) -> {
                final String keyPath = path + ".step-actions." + key;
                try {
                    final int step = Integer.parseInt(String.valueOf(key));
                    stepActions.put(step, strings(value, keyPath, errors));
                } catch (final NumberFormatException e) {
                    errors.add(ConfigIssue.error(FILE, keyPath, "must be a step number from 1"));
                }
            });
        }
        final Map<Integer, String> stepMessages = new TreeMap<>();
        final ConfigurationNode stepMessagesNode = node.node("step-messages");
        if (!stepMessagesNode.virtual() && !stepMessagesNode.isMap()) {
            errors.add(ConfigIssue.error(FILE, path + ".step-messages", "must be a section"));
        }
        stepMessagesNode.childrenMap().forEach((key, value) -> {
            final String keyPath = path + ".step-messages." + key;
            try {
                final int step = Integer.parseInt(String.valueOf(key));
                if (value.isMap() || value.isList() || value.raw() == null) {
                    errors.add(ConfigIssue.error(FILE, keyPath, "must be a text"));
                } else {
                    stepMessages.put(step, String.valueOf(value.raw()));
                }
            } catch (final NumberFormatException e) {
                errors.add(ConfigIssue.error(FILE, keyPath, "must be a step number from 1"));
            }
        });
        final List<TemplateStep> ladder = new ArrayList<>();
        final List<String> steps = strings(node.node("ladder"), path + ".ladder", errors);
        for (int i = 0; i < steps.size(); i++) {
            final TemplateSteps.Parsed step = TemplateSteps.parse(steps.get(i));
            if (step.error() != null) {
                errors.add(ConfigIssue.error(FILE, path + ".ladder[" + i + "]", step.error()));
                continue;
            }
            try {
                ladder.add(TemplateStep.of(step.type(), step.duration(),
                        stepActions.getOrDefault(i + 1, List.of()), stepMessages.get(i + 1)));
            } catch (final ValidationException e) {
                report(errors, path + ".ladder[" + i + "]", e.errors());
            }
        }
        for (final String key : List.of("step-actions", "step-messages")) {
            (key.equals("step-actions") ? stepActions.keySet() : stepMessages.keySet()).stream()
                    .filter(step -> step < 1 || step > steps.size())
                    .forEach(step -> errors.add(ConfigIssue.error(FILE, path + "." + key + "."
                            + step, "the ladder has no step " + step)));
        }
        final ConfigurationNode messageNode = node.node("message");
        String message = null;
        if (!messageNode.virtual()) {
            if (messageNode.isMap() || messageNode.isList() || messageNode.raw() == null) {
                errors.add(ConfigIssue.error(FILE, path + ".message", "must be a text"));
            } else {
                message = String.valueOf(messageNode.raw());
            }
        }
        final Duration expiry = expiry(node.node("ladder-expiry"), path, errors);
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
        if (errors.size() > before || type == null) {
            return Optional.empty();
        }
        final TemplateDefinition.Builder builder = TemplateDefinition.builder(name, type)
                .ip(Boolean.TRUE.equals(ip))
                .shadow(Boolean.TRUE.equals(shadow))
                .scope(scope)
                .reason(node.node("reason").getString(""))
                .ladder(ladder)
                .ladderExpiry(expiry)
                .addressShared(Boolean.TRUE.equals(addressShared))
                .permissionRequired(Boolean.TRUE.equals(permissionRequired))
                .actions(actions)
                .groups(groups)
                .message(message);
        final List<FieldError> problems = builder.validate();
        if (!problems.isEmpty()) {
            report(errors, path, problems);
            return Optional.empty();
        }
        return Optional.of(builder.build());
    }

    private static @Nullable PunishmentType type(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        final String text = node.getString("").strip();
        final Optional<PunishmentType> type = Arrays.stream(PunishmentType.values())
                .filter(value -> value.name().equalsIgnoreCase(text))
                .findFirst();
        if (type.isEmpty()) {
            errors.add(ConfigIssue.error(FILE, path + ".type", text.isEmpty() ? "is required"
                    : "\"" + text + "\" is not a type (ban, mute, warn or kick)"));
        }
        return type.orElse(null);
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

    private static @Nullable Duration expiry(final ConfigurationNode node, final String path,
            final List<ConfigIssue> errors) {
        final String text = node.getString("");
        if (text.isBlank() || text.equalsIgnoreCase("none")) {
            return null;
        }
        final Optional<Duration> parsed = Durations.parse(text);
        if (parsed.isEmpty()) {
            errors.add(ConfigIssue.error(FILE, path + ".ladder-expiry",
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
            root.node("templates", template.name()).set(tree(template));
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
        tree.put("type", template.type().name().toLowerCase(Locale.ROOT));
        if (template.ip()) {
            tree.put("ip", true);
        }
        if (template.shadow()) {
            tree.put("shadow", true);
        }
        template.scope().ifPresent(scope -> tree.put("scope", Scopes.format(scope)));
        tree.put("reason", template.reason());
        final List<String> ladder = new ArrayList<>();
        final Map<String, Object> stepActions = new LinkedHashMap<>();
        final Map<String, Object> stepMessages = new LinkedHashMap<>();
        for (int i = 0; i < template.ladder().size(); i++) {
            final TemplateStep step = template.ladder().get(i);
            ladder.add(TemplateSteps.format(step, template.type()));
            if (!step.actions().isEmpty()) {
                stepActions.put(Integer.toString(i + 1), List.copyOf(step.actions()));
            }
            final int number = i + 1;
            step.message().ifPresent(text -> stepMessages.put(Integer.toString(number), text));
        }
        tree.put("ladder", ladder);
        template.ladderExpiry().ifPresent(expiry -> tree.put("ladder-expiry", duration(expiry)));
        if (template.addressShared()) {
            tree.put("address-shared", true);
        }
        if (template.permissionRequired()) {
            tree.put("permission-required", true);
        }
        if (!template.actions().isEmpty()) {
            tree.put("actions", List.copyOf(template.actions()));
        }
        if (!stepActions.isEmpty()) {
            tree.put("step-actions", stepActions);
        }
        if (!template.groups().isEmpty()) {
            tree.put("groups", new TreeMap<>(template.groups()));
        }
        template.message().ifPresent(text -> tree.put("message", text));
        if (!stepMessages.isEmpty()) {
            tree.put("step-messages", stepMessages);
        }
        return tree;
    }

    private static String duration(final Duration duration) {
        return Durations.formatCompact(duration).replace(" ", "");
    }

    private static String describe(final Exception e) {
        final String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.strip();
    }

}
