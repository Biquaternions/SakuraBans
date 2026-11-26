package net.serlith.sakurabans.core.command.module;

import java.util.ArrayList;
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
import net.serlith.sakurabans.core.admin.SakuraMessageTemplateService;
import net.serlith.sakurabans.core.admin.SakuraSettingsService;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.permission.Permissions;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jspecify.annotations.Nullable;

public final class OverrideCommands implements CommandModule {

    private static final String CONFIRM = "confirm";
    private static final String ALL = "all";

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final SakuraSettingsService settings;
    private final SakuraMessageTemplateService messages;
    private final ConfirmationTracker confirmations;

    public OverrideCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final SakuraSettingsService settings, final SakuraMessageTemplateService messages,
            final ConfirmationTracker confirmations) {
        this.registrar = registrar;
        this.runner = runner;
        this.settings = settings;
        this.messages = messages;
        this.confirmations = confirmations;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_SETTINGS)
                .ifPresent(root -> {
                    final var settings = root.literal("settings")
                            .permission(Permissions.ADMIN_SETTINGS);
                    manager.command(settings.literal("list")
                            .handler(context -> this.listSettings(context.sender())));
                    manager.command(settings.literal("reset")
                            .required("key", StringParser.stringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            this.settingKeys()))
                            .flag(CommandFlag.builder(CONFIRM).build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.resetSettings(context))));
                });
        this.registrar.builder(manager, SakuraCommands.ROOT,
                        MessageKey.COMMAND_DESCRIPTION_MESSAGES)
                .ifPresent(root -> {
                    final var messages = root.literal("messages")
                            .permission(Permissions.ADMIN_MESSAGES);
                    manager.command(messages.literal("list")
                            .optional("locale", StringParser.stringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            this.locales()))
                            .handler(context -> this.listMessages(context.sender(),
                                    context.<String>optional("locale").orElse(null))));
                    manager.command(messages.literal("reset")
                            .required("locale", StringParser.stringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            this.locales()))
                            .required("key", StringParser.stringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            this.messageKeys(context.get("locale"))))
                            .flag(CommandFlag.builder(CONFIRM).build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.resetMessages(context))));
                });
    }

    private List<String> settingKeys() {
        final List<String> keys = new ArrayList<>(this.settings.overrides().keySet());
        if (!keys.isEmpty()) {
            keys.add(ALL);
        }
        return keys;
    }

    private List<String> locales() {
        return this.messages.overrides().keySet().stream().map(Locale::toLanguageTag).toList();
    }

    private List<String> messageKeys(final String locale) {
        final List<String> keys = new ArrayList<>(this.messages.overrides()
                .getOrDefault(Locale.forLanguageTag(locale.replace('_', '-')), new TreeSet<>()));
        if (!keys.isEmpty()) {
            keys.add(ALL);
        }
        return keys;
    }

    private void listSettings(final CommandActor actor) {
        final SortedMap<String, String> overrides = this.settings.overrides();
        if (overrides.isEmpty()) {
            this.runner.send(actor, MessageKey.SETTINGS_LIST_EMPTY);
            return;
        }
        this.runner.send(actor, MessageKey.SETTINGS_LIST_HEADER,
                Placeholder.unparsed("count", Integer.toString(overrides.size())));
        overrides.forEach((key, value) -> this.runner.send(actor, MessageKey.SETTINGS_LIST_ENTRY,
                Placeholder.unparsed("key", key),
                Placeholder.unparsed("file", this.settings.fileValue(key)),
                Placeholder.unparsed("database", value.strip())));
    }

    private CompletableFuture<?> resetSettings(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final String key = context.<String>get("key").strip();
        final Set<String> overridden = this.settings.overrides().keySet();
        final boolean all = key.equalsIgnoreCase(ALL);
        if (all ? overridden.isEmpty() : !overridden.contains(key)) {
            this.runner.send(actor, MessageKey.SETTINGS_NOT_OVERRIDDEN,
                    Placeholder.unparsed("key", key));
            return CompletableFuture.completedFuture(null);
        }
        final String count = Integer.toString(overridden.size());
        if (all && !this.confirmed(context, "settings reset all")) {
            this.runner.send(actor, MessageKey.SETTINGS_RESET_CONFIRM,
                    Placeholder.unparsed("count", count));
            return CompletableFuture.completedFuture(null);
        }
        return this.settings.reset(all ? Set.copyOf(overridden) : Set.of(key),
                        this.settings.settings().version(), actor.asActor())
                .thenAccept(result -> this.reply(actor, result, all
                        ? MessageKey.SETTINGS_RESET_ALL_DONE : MessageKey.SETTINGS_RESET_DONE,
                        Map.of("key", key, "count", count)));
    }

    private void listMessages(final CommandActor actor, final @Nullable String locale) {
        final SortedMap<Locale, SortedSet<String>> overrides = this.messages.overrides();
        if (locale != null) {
            overrides.keySet().retainAll(Set.of(Locale.forLanguageTag(locale.replace('_', '-'))));
        }
        if (overrides.isEmpty()) {
            this.runner.send(actor, MessageKey.MESSAGES_LIST_EMPTY);
            return;
        }
        overrides.forEach((tag, keys) -> {
            this.runner.send(actor, MessageKey.MESSAGES_LIST_HEADER,
                    Placeholder.unparsed("locale", tag.toLanguageTag()),
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
        final Set<String> overridden = this.messages.overrides().getOrDefault(locale,
                new TreeSet<>());
        final boolean all = key.equalsIgnoreCase(ALL);
        if (all ? overridden.isEmpty() : !overridden.contains(key)) {
            this.runner.send(actor, MessageKey.MESSAGES_NOT_OVERRIDDEN,
                    Placeholder.unparsed("locale", tag), Placeholder.unparsed("key", key));
            return CompletableFuture.completedFuture(null);
        }
        final String count = Integer.toString(overridden.size());
        if (all && !this.confirmed(context, "messages reset " + tag + " all")) {
            this.runner.send(actor, MessageKey.MESSAGES_RESET_CONFIRM,
                    Placeholder.unparsed("locale", tag), Placeholder.unparsed("count", count));
            return CompletableFuture.completedFuture(null);
        }
        return this.messages.reset(locale, all ? Set.copyOf(overridden) : Set.of(key),
                        this.messages.messages(locale).version(), actor.asActor())
                .thenAccept(result -> this.reply(actor, result, all
                        ? MessageKey.MESSAGES_RESET_ALL_DONE : MessageKey.MESSAGES_RESET_DONE,
                        Map.of("key", key, "count", count, "locale", tag)));
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
