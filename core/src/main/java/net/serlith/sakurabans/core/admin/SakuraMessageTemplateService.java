package net.serlith.sakurabans.core.admin;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.permission.Action;
import net.serlith.sakurabans.core.permission.AuthorizationService;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import org.jspecify.annotations.Nullable;

public final class SakuraMessageTemplateService {

    private static final Pattern TAG = Pattern.compile("<([a-z_]+)>");
    private static final int MAX_TEXT = 4096;

    private final MessageService messages;
    private final OverrideRepository repository;
    private final AuthorizationService authorization;
    private final SyncService sync;
    private final InstantSource clock;
    private final AtomicReference<Map<Scope, Map<Locale, Long>>> versions =
            new AtomicReference<>(Map.of());
    private final AtomicReference<@Nullable List<MessageField>> schema = new AtomicReference<>();

    public SakuraMessageTemplateService(final MessageService messages,
            final OverrideRepository repository, final AuthorizationService authorization,
            final SyncService sync, final InstantSource clock) {
        this.messages = messages;
        this.repository = repository;
        this.authorization = authorization;
        this.sync = sync;
        this.clock = clock;
    }

    public CompletableFuture<Void> reload() {
        return this.repository.messages().thenAccept(stored -> {
            final Map<Scope, Map<Locale, Long>> versions = new HashMap<>();
            final Map<Scope, Map<Locale, Map<String, String>>> texts = new HashMap<>();
            stored.scopes().forEach((scope, locales) -> locales.forEach((locale, messages) -> {
                versions.computeIfAbsent(scope, ignored -> new HashMap<>())
                        .put(locale, messages.version());
                texts.computeIfAbsent(scope, ignored -> new HashMap<>())
                        .put(locale, messages.texts());
            }));
            this.versions.set(Map.copyOf(versions));
            this.messages.overrides(texts);
        });
    }

    public List<MessageField> schema() {
        final List<MessageField> cached = this.schema.get();
        if (cached != null) {
            return cached;
        }
        final Map<String, String> english = MessageService.builtInTexts();
        final List<MessageField> fields = new ArrayList<>();
        for (final MessageKey key : MessageKey.all()) {
            final String text = english.getOrDefault(key.key(), "");
            fields.add(new MessageField(key.key(), placeholders(text), text,
                    key.key()));
        }
        final List<MessageField> all = List.copyOf(fields);
        this.schema.set(all);
        return all;
    }

    static List<String> placeholders(final String text) {
        final Set<String> names = new LinkedHashSet<>();
        final Matcher matcher = TAG.matcher(text);
        while (matcher.find()) {
            final String name = matcher.group(1);
            if (!name.equals("prefix") && !TagResolver.standard().has(name)) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    /** A scope's overridden keys by locale. */
    public SortedMap<Locale, SortedSet<String>> overrides(final Scope scope) {
        final SortedMap<Locale, SortedSet<String>> overrides =
                new TreeMap<>(Comparator.comparing(Locale::toLanguageTag));
        this.messages.scopedOverrides().getOrDefault(scope, Map.of()).forEach((locale, texts) -> {
            if (!texts.isEmpty()) {
                overrides.put(locale, new TreeSet<>(texts.keySet()));
            }
        });
        return overrides;
    }

    /** A scope's override texts in a locale. */
    public Map<String, String> texts(final Scope scope, final Locale locale) {
        return this.messages.scopedOverrides().getOrDefault(scope, Map.of())
                .getOrDefault(locale, Map.of());
    }

    /** Every scope and locale that has overrides, with their texts. */
    public Map<Scope, Map<Locale, Map<String, String>>> allOverrides() {
        return this.messages.scopedOverrides();
    }

    /** The scope whose override gives a key its text here, if one does. */
    public Optional<Scope> source(final String key, final Locale locale) {
        return this.messages.source(key, locale);
    }

    public MessageSnapshot messages(final Locale locale) {
        final Map<String, String> texts = new LinkedHashMap<>();
        final Map<String, ValueOrigin> origins = new LinkedHashMap<>();
        final Map<String, String> overridden =
                this.messages.overrides().getOrDefault(locale, Map.of());
        for (final MessageKey key : MessageKey.all()) {
            this.messages.find(key.key(), locale).ifPresent(text -> texts.put(key.key(), text));
            origins.put(key.key(), overridden.containsKey(key.key()) ? ValueOrigin.OVERRIDE
                    : this.messages.fileText(key.key(), locale).isPresent()
                            ? ValueOrigin.FILE : ValueOrigin.DEFAULT);
        }
        return new MessageSnapshot(this.version(locale), locale, texts, origins);
    }

    /** The version of the global overrides of a locale: 0 while it never had any. */
    public long version(final Locale locale) {
        return this.version(GlobalScope.INSTANCE, locale);
    }

    /** The version of a scope's overrides in a locale: 0 while it never had any. */
    public long version(final Scope scope, final Locale locale) {
        return this.versions.get().getOrDefault(scope, Map.of()).getOrDefault(locale, 0L);
    }

    /** The locales that have a language file or overrides. */
    public SortedSet<Locale> locales() {
        final SortedSet<Locale> locales = new TreeSet<>(Comparator.comparing(Locale::toString));
        locales.addAll(this.messages.fileLocales());
        this.messages.scopedOverrides().values().forEach(byLocale ->
                byLocale.forEach((locale, texts) -> {
                    if (!texts.isEmpty()) {
                        locales.add(locale);
                    }
                }));
        return locales;
    }

    /** The language file's text, or the shipped en_US one. */
    public String fileText(final String key, final Locale locale) {
        return this.messages.fileText(key, locale)
                .orElseGet(() -> MessageService.builtInTexts().getOrDefault(key, ""));
    }

    public CompletableFuture<AdminResult<MessageSnapshot>> update(final Locale locale,
            final Map<String, String> patch, final long expectedVersion, final Actor actor) {
        return this.change(locale, patch, Set.of(), expectedVersion, actor);
    }

    public CompletableFuture<AdminResult<MessageSnapshot>> reset(final Locale locale,
            final Set<String> keys, final long expectedVersion, final Actor actor) {
        return this.change(locale, Map.of(), keys, expectedVersion, actor);
    }

    public CompletableFuture<AdminResult<MessageSnapshot>> change(final Locale locale,
            final Map<String, String> set, final Set<String> reset, final long expectedVersion,
            final Actor actor) {
        return this.change(GlobalScope.INSTANCE, locale, set, reset, expectedVersion, actor);
    }

    /** Sets and resets for one scope and locale in one write, with their version. */
    public CompletableFuture<AdminResult<MessageSnapshot>> change(final Scope scope,
            final Locale locale, final Map<String, String> set, final Set<String> reset,
            final long expectedVersion, final Actor actor) {
        final Map<String, @Nullable String> changes = new HashMap<>();
        final List<FieldError> errors = this.prepare(set, reset, changes);
        return this.write(scope, locale, changes, errors, expectedVersion, actor);
    }

    /** Unknown keys and texts that are empty or too long; paths are the message keys. */
    public List<FieldError> validate(final Map<String, String> set, final Set<String> reset) {
        return this.prepare(set, reset, new HashMap<>());
    }

    /** The overrides the change really alters (a text equal to the override's doesn't). */
    public Map<String, @Nullable String> effective(final Scope scope, final Locale locale,
            final Map<String, String> set, final Set<String> reset) {
        final Map<String, @Nullable String> changes = new HashMap<>();
        this.prepare(set, reset, changes);
        final Map<String, String> current = this.texts(scope, locale);
        changes.entrySet().removeIf(change -> change.getValue() == null
                ? !current.containsKey(change.getKey())
                : change.getValue().equals(current.get(change.getKey())));
        return changes;
    }

    private List<FieldError> prepare(final Map<String, String> set, final Set<String> reset,
            final Map<String, @Nullable String> changes) {
        final List<FieldError> errors = new ArrayList<>();
        set.forEach((key, text) -> {
            if (!this.known(key, errors)) {
                return;
            }
            if (text == null || text.isBlank() || text.length() > MAX_TEXT) {
                errors.add(FieldError.of(key, FieldError.Code.INVALID,
                        "must be 1-" + MAX_TEXT + " characters"));
                return;
            }
            changes.put(key, text);
        });
        for (final String key : reset) {
            if (this.known(key, errors)) {
                changes.put(key, null);
            }
        }
        return errors;
    }

    public Component preview(final String text, final Map<String, String> sample) {
        final List<TagResolver> resolvers = new ArrayList<>();
        sample.forEach((name, value) -> resolvers.add(Placeholder.unparsed(name, value)));
        resolvers.add(Placeholder.component("prefix", MiniMessage.miniMessage().deserialize(
                this.messages.find(MessageKey.PREFIX.key(), this.messages.defaultLocale())
                        .orElse(""))));
        return MiniMessage.miniMessage().deserialize(text, TagResolver.resolver(resolvers));
    }

    private boolean known(final String key, final List<FieldError> errors) {
        if (MessageKey.all().stream().noneMatch(candidate -> candidate.key().equals(key))) {
            errors.add(FieldError.of(key, FieldError.Code.INVALID, "is not a message key"));
            return false;
        }
        return true;
    }

    private CompletableFuture<AdminResult<MessageSnapshot>> write(final Scope scope,
            final Locale locale,
            final Map<String, @Nullable String> changes, final List<FieldError> errors,
            final long expectedVersion, final Actor actor) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.authorization.check(checked, new Action.Manage(Action.Manage.Area.MESSAGES))
                .thenCompose(decision -> {
                    if (!decision.allowed()) {
                        return CompletableFuture.completedFuture(
                                new AdminResult.Forbidden<>());
                    }
                    if (!errors.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new AdminResult.ValidationFailed<>(errors));
                    }
                    return this.repository.changeMessages(scope, locale, changes,
                            expectedVersion,
                            checked, this.clock.instant()).thenCompose(written -> {
                                if (written.conflict()) {
                                    return CompletableFuture.completedFuture(
                                            new AdminResult.Conflict<>(
                                                    written.version()));
                                }
                                return this.reload().thenApply(ignored -> {
                                    this.sync.publish(new SyncPacket.EntityChangedPacket(
                                            SubjectType.MESSAGE));
                                    return new AdminResult.Ok<>(this.messages(locale));
                                });
                            });
                });
    }
}
