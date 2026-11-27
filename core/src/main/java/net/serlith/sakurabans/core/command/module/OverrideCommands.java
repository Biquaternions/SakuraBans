package net.serlith.sakurabans.core.command.module;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.admin.EditorLines;
import net.serlith.sakurabans.core.admin.InstanceRegistry;
import net.serlith.sakurabans.core.admin.SakuraMessageTemplateService;
import net.serlith.sakurabans.core.admin.SakuraSettingsService;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.parser.ScopeParser;
import net.serlith.sakurabans.core.configuration.EnvironmentOverrides;
import net.serlith.sakurabans.core.configuration.SettingsSchema;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.repository.InstanceRepository;
import net.serlith.sakurabans.core.util.Scopes;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jspecify.annotations.Nullable;

public final class OverrideCommands implements CommandModule {

    private static final String CONFIRM = "confirm";
    private static final String EFFECTIVE = "effective";
    private static final String SCOPE = "scope";
    private static final String ALL = "all";

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final SakuraSettingsService settings;
    private final SakuraMessageTemplateService messages;
    private final ConfirmationTracker confirmations;
    private final ScopeParser scopes;
    private final InstanceRegistry registry;

    public OverrideCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final SakuraSettingsService settings, final SakuraMessageTemplateService messages,
            final ConfirmationTracker confirmations, final ScopeParser scopes,
            final InstanceRegistry registry) {
        this.registrar = registrar;
        this.runner = runner;
        this.settings = settings;
        this.messages = messages;
        this.confirmations = confirmations;
        this.scopes = scopes;
        this.registry = registry;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_SETTINGS)
                .ifPresent(root -> {
                    final var settings = root.literal("settings")
                            .permission(Permissions.ADMIN_SETTINGS);
                    // No scope means global, never scopes.default.
                    manager.command(settings.literal("list")
                            .optional(SCOPE, this.scopes.configDescriptor())
                            .flag(CommandFlag.builder(EFFECTIVE).build())
                            .handler(context -> this.listSettings(context.sender(),
                                    context.<Scope>optional(SCOPE)
                                            .orElse(GlobalScope.INSTANCE),
                                    context.flags().isPresent(EFFECTIVE))));
                    manager.command(settings.literal("reset")
                            .required("key", StringParser.stringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            this.settingKeys(context.<Scope>optional(SCOPE)
                                                    .orElse(GlobalScope.INSTANCE))))
                            .optional(SCOPE, this.scopes.configDescriptor())
                            .flag(CommandFlag.builder(CONFIRM).build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.resetSettings(context))));
                    manager.command(settings.literal("publish")
                            .optional("keys", StringParser.greedyFlagYieldingStringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            List.copyOf(this.settings.publishable(
                                                    this.registry.self(), Set.of()).keySet())))
                            .flag(CommandFlag.builder(CONFIRM).build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.publish(context))));
                });
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_MESSAGES)
                .ifPresent(root -> {
                    final var messages = root.literal("messages")
                            .permission(Permissions.ADMIN_MESSAGES);
                    // The scope comes last; a list without a locale may still name one.
                    manager.command(messages.literal("list")
                            .optional("locale", StringParser.stringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            this.locales()))
                            .optional(SCOPE, this.scopes.configDescriptor())
                            .handler(context -> {
                                final String locale =
                                        context.<String>optional("locale").orElse(null);
                                final Scope named = context.<Scope>optional(SCOPE).orElse(null);
                                final Scope instead = locale == null || named != null ? null
                                        : Scopes.parse(locale).orElse(null);
                                this.listMessages(context.sender(),
                                        instead != null ? null : locale, instead != null
                                                ? instead : named != null ? named
                                                : GlobalScope.INSTANCE);
                            }));
                    manager.command(messages.literal("reset")
                            .required("locale", StringParser.stringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            this.locales()))
                            .required("key", StringParser.stringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            this.messageKeys(context.get("locale"))))
                            .optional(SCOPE, this.scopes.configDescriptor())
                            .flag(CommandFlag.builder(CONFIRM).build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.resetMessages(context))));
                });
    }

    private List<String> settingKeys(final Scope scope) {
        final List<String> keys = new ArrayList<>(this.settings.overrides(scope).keySet());
        if (!keys.isEmpty()) {
            keys.add(ALL);
        }
        return keys;
    }

    private List<String> locales() {
        final Set<String> locales = new TreeSet<>();
        this.messages.allOverrides().values().forEach(byLocale -> byLocale.keySet()
                .forEach(locale -> locales.add(locale.toLanguageTag())));
        return List.copyOf(locales);
    }

    private List<String> messageKeys(final String locale) {
        final Locale parsed = Locale.forLanguageTag(locale.replace('_', '-'));
        final Set<String> overridden = new TreeSet<>();
        this.messages.allOverrides().values().forEach(byLocale -> overridden.addAll(
                byLocale.getOrDefault(parsed, Map.of()).keySet()));
        final List<String> keys = new ArrayList<>(overridden);
        if (!keys.isEmpty()) {
            keys.add(ALL);
        }
        return keys;
    }

    private void listSettings(final CommandActor actor, final Scope scope,
            final boolean effective) {
        if (effective) {
            this.runner.send(actor, MessageKey.SETTINGS_EFFECTIVE_HEADER,
                    Placeholder.unparsed("scopes", String.join(", ", this.settings.layers()
                            .stream().map(Scopes::format).toList())));
            for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
                if (!entry.editable()) {
                    continue;
                }
                final String layer = this.settings.source(entry.key()).map(Scopes::format)
                        .orElseGet(() -> this.settings.origin(entry.key()).name()
                                .toLowerCase(Locale.ROOT));
                this.runner.send(actor, MessageKey.SETTINGS_EFFECTIVE_ENTRY,
                        Placeholder.unparsed("key", entry.key()),
                        this.value(actor, "value", entry.key(),
                                this.settings.plainValue(entry.key()).orElse(null)),
                        Placeholder.unparsed("layer", layer));
            }
            final List<EnvironmentOverrides.Applied> environment = this.settings.environment();
            if (!environment.isEmpty()) {
                this.runner.send(actor, MessageKey.SETTINGS_EFFECTIVE_ENVIRONMENT_HEADER,
                        Placeholder.unparsed("count", Integer.toString(environment.size())));
                for (final EnvironmentOverrides.Applied applied : environment) {
                    this.runner.send(actor, MessageKey.SETTINGS_EFFECTIVE_ENVIRONMENT_ENTRY,
                            Placeholder.unparsed("source", applied.source()),
                            Placeholder.unparsed("key", applied.key()),
                            applied.secret()
                                    ? Placeholder.component("value", this.runner.render(actor,
                                            MessageKey.SETTINGS_SECRET_SET))
                                    : Placeholder.unparsed("value",
                                            EditorLines.shorten(applied.value())));
                }
            }
            return;
        }
        final String name = Scopes.format(scope);
        final SortedMap<String, String> overrides = this.settings.overrides(scope);
        if (overrides.isEmpty()) {
            this.runner.send(actor, MessageKey.SETTINGS_LIST_EMPTY,
                    Placeholder.unparsed("scope", name));
            return;
        }
        this.runner.send(actor, MessageKey.SETTINGS_LIST_HEADER,
                Placeholder.unparsed("scope", name),
                Placeholder.unparsed("count", Integer.toString(overrides.size())));
        overrides.forEach((key, value) -> this.runner.send(actor, MessageKey.SETTINGS_LIST_ENTRY,
                Placeholder.unparsed("key", key),
                this.value(actor, "file", key, this.settings.plainFileValue(key).orElse(null)),
                this.value(actor, "database", key, this.settings.decoded(value))));
    }

    /** A value for a reply; a secret only says whether it is set. */
    private TagResolver value(final CommandActor actor, final String name, final String key,
            final @Nullable Object value) {
        if (SettingsSchema.secret(key)) {
            return Placeholder.component(name, this.runner.render(actor,
                    value == null || String.valueOf(value).isEmpty()
                            ? MessageKey.SETTINGS_SECRET_EMPTY : MessageKey.SETTINGS_SECRET_SET));
        }
        return Placeholder.unparsed(name, EditorLines.shorten(value));
    }

    private CompletableFuture<?> resetSettings(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final Scope scope = context.<Scope>optional(SCOPE).orElse(GlobalScope.INSTANCE);
        final String name = Scopes.format(scope);
        final String key = context.<String>get("key").strip();
        final Set<String> overridden = this.settings.overrides(scope).keySet();
        final boolean all = key.equalsIgnoreCase(ALL);
        if (all ? overridden.isEmpty() : !overridden.contains(key)) {
            this.runner.send(actor, MessageKey.SETTINGS_NOT_OVERRIDDEN,
                    Placeholder.unparsed("key", key), Placeholder.unparsed("scope", name));
            return CompletableFuture.completedFuture(null);
        }
        final String count = Integer.toString(overridden.size());
        if (all && !this.confirmed(context, "settings reset all " + name)) {
            this.runner.send(actor, MessageKey.SETTINGS_RESET_CONFIRM,
                    Placeholder.unparsed("count", count), Placeholder.unparsed("scope", name));
            return CompletableFuture.completedFuture(null);
        }
        return this.settings.change(scope, Map.of(), all ? Set.copyOf(overridden) : Set.of(key),
                        this.settings.version(scope), actor.asActor())
                .thenAccept(result -> this.reply(actor, result, all
                        ? MessageKey.SETTINGS_RESET_ALL_DONE : MessageKey.SETTINGS_RESET_DONE,
                        Map.of("key", key, "count", count, "scope", name)));
    }

    private CompletableFuture<?> publish(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final InstanceRepository.Instance self = this.registry.self();
        if (self.serverName().isEmpty()) {
            this.runner.send(actor, MessageKey.SETTINGS_PUBLISH_UNNAMED);
            return CompletableFuture.completedFuture(null);
        }
        final Set<String> keys = new LinkedHashSet<>();
        context.<String>optional("keys").ifPresent(text ->
                keys.addAll(List.of(text.strip().split("\\s+"))));
        final SortedMap<String, List<@Nullable Object>> diff =
                this.settings.publishable(self, keys);
        if (diff.isEmpty()) {
            this.runner.send(actor, MessageKey.SETTINGS_PUBLISH_NONE,
                    Placeholder.unparsed("server", self.serverName()));
            return CompletableFuture.completedFuture(null);
        }
        this.runner.send(actor, MessageKey.SETTINGS_PUBLISH_HEADER,
                Placeholder.unparsed("server", self.serverName()),
                Placeholder.unparsed("count", Integer.toString(diff.size())));
        diff.forEach((key, values) -> this.runner.send(actor, MessageKey.SETTINGS_PUBLISH_ENTRY,
                Placeholder.unparsed("key", key),
                this.value(actor, "before", key, values.get(0)),
                this.value(actor, "after", key, values.get(1))));
        if (!this.confirmed(context, "settings publish " + String.join(" ", diff.keySet()))) {
            this.runner.send(actor, MessageKey.SETTINGS_PUBLISH_CONFIRM);
            return CompletableFuture.completedFuture(null);
        }
        return this.settings.publish(self, diff.keySet(), this.settings.version(),
                actor.asActor()).thenAccept(result -> this.reply(actor, result,
                        MessageKey.SETTINGS_PUBLISH_DONE, Map.of("count",
                                Integer.toString(diff.size()), "server", self.serverName())));
    }

    private void listMessages(final CommandActor actor, final @Nullable String locale,
            final Scope scope) {
        final String name = Scopes.format(scope);
        final SortedMap<Locale, SortedSet<String>> overrides = this.messages.overrides(scope);
        if (locale != null) {
            overrides.keySet().retainAll(Set.of(Locale.forLanguageTag(locale.replace('_', '-'))));
        }
        if (overrides.isEmpty()) {
            this.runner.send(actor, MessageKey.MESSAGES_LIST_EMPTY,
                    Placeholder.unparsed("scope", name));
            return;
        }
        overrides.forEach((tag, keys) -> {
            this.runner.send(actor, MessageKey.MESSAGES_LIST_HEADER,
                    Placeholder.unparsed("locale", tag.toLanguageTag()),
                    Placeholder.unparsed("scope", name),
                    Placeholder.unparsed("count", Integer.toString(keys.size())));
            keys.forEach(key -> this.runner.send(actor, MessageKey.MESSAGES_LIST_ENTRY,
                    Placeholder.unparsed("key", key)));
        });
    }

    private CompletableFuture<?> resetMessages(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final Locale locale = Locale.forLanguageTag(context.<String>get("locale").strip()
                .replace('_', '-'));
        final String tag = locale.toLanguageTag();
        final String key = context.<String>get("key").strip();
        final Scope scope = context.<Scope>optional(SCOPE).orElse(GlobalScope.INSTANCE);
        final String name = Scopes.format(scope);
        final Set<String> overridden = this.messages.texts(scope, locale).keySet();
        final boolean all = key.equalsIgnoreCase(ALL);
        if (all ? overridden.isEmpty() : !overridden.contains(key)) {
            this.runner.send(actor, MessageKey.MESSAGES_NOT_OVERRIDDEN,
                    Placeholder.unparsed("locale", tag), Placeholder.unparsed("key", key),
                    Placeholder.unparsed("scope", name));
            return CompletableFuture.completedFuture(null);
        }
        final String count = Integer.toString(overridden.size());
        if (all && !this.confirmed(context, "messages reset " + tag + " all " + name)) {
            this.runner.send(actor, MessageKey.MESSAGES_RESET_CONFIRM,
                    Placeholder.unparsed("locale", tag), Placeholder.unparsed("count", count),
                    Placeholder.unparsed("scope", name));
            return CompletableFuture.completedFuture(null);
        }
        return this.messages.change(scope, locale, Map.of(),
                        all ? Set.copyOf(overridden) : Set.of(key),
                        this.messages.version(scope, locale), actor.asActor())
                .thenAccept(result -> this.reply(actor, result, all
                        ? MessageKey.MESSAGES_RESET_ALL_DONE : MessageKey.MESSAGES_RESET_DONE,
                        Map.of("key", key, "count", count, "locale", tag, "scope", name)));
    }

    private boolean confirmed(final CommandContext<CommandActor> context, final String command) {
        final String sender = context.sender().name();
        if (context.flags().isPresent(CONFIRM) && this.confirmations.confirm(sender, command)) {
            return true;
        }
        this.confirmations.request(sender, command);
        return false;
    }

    private void reply(final CommandActor actor, final AdminResult<?> result,
            final MessageKey done, final Map<String, String> values) {
        switch (result) {
            case AdminResult.Ok<?> ok -> this.runner.send(actor, done, values.entrySet().stream()
                    .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                    .toArray(TagResolver[]::new));
            case AdminResult.ValidationFailed<?> failed -> this.runner.send(actor,
                    MessageKey.OVERRIDES_INVALID, Placeholder.unparsed("details",
                            String.join("; ", failed.errors().stream()
                                    .map(error -> error.path() + " " + error.message())
                                    .toList())));
            case AdminResult.Forbidden<?> forbidden ->
                    this.runner.send(actor, MessageKey.OVERRIDES_FORBIDDEN);
            case AdminResult.Conflict<?> conflict ->
                    this.runner.send(actor, MessageKey.OVERRIDES_CONFLICT);
            case AdminResult.NotFound<?> notFound ->
                    this.runner.send(actor, MessageKey.OVERRIDES_CONFLICT);
        }
    }
}
