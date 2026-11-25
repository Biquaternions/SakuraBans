package net.serlith.sakurabans.core.admin;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.message.MessageField;
import net.serlith.sakurabans.api.message.MessageSnapshot;
import net.serlith.sakurabans.api.message.MessageTemplateService;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.settings.ValueOrigin;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraOverridesChangedEvents;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraAdminResult;
import net.serlith.sakurabans.core.model.SakuraSettings;
import net.serlith.sakurabans.core.punish.ActionCheck;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import org.jspecify.annotations.Nullable;

public final class SakuraMessageTemplateService implements MessageTemplateService {

    private static final Pattern TAG = Pattern.compile("<([a-z_]+)>");
    private static final Pattern ANY_TAG = Pattern.compile("</?!?([A-Za-z0-9_#-]+)[:>]");
    private static final int MAX_TEXT = 4096;

    private final MessageService messages;
    private final OverrideRepository repository;
    private final ActionCheck actions;
    private final SyncService sync;
    private final SakuraEventBus events;
    private final InstantSource clock;
    private final AtomicLong version = new AtomicLong(1);
    private final AtomicReference<@Nullable List<MessageField>> schema = new AtomicReference<>();

    public SakuraMessageTemplateService(final MessageService messages,
            final OverrideRepository repository, final ActionCheck actions,
            final SyncService sync, final SakuraEventBus events, final InstantSource clock) {
        this.messages = messages;
        this.repository = repository;
        this.actions = actions;
        this.sync = sync;
        this.events = events;
        this.clock = clock;
    }

    public CompletableFuture<Void> reload() {
        return this.repository.messages().thenAccept(stored -> {
            this.version.set(stored.version());
            this.messages.overrides(stored.texts());
        });
    }

    public CompletableFuture<Void> changed(final SyncPacket.EntityChangedPacket packet) {
        final String id = packet.id();
        final Locale locale = Locale.forLanguageTag(id.contains(":")
                ? id.substring(0, id.indexOf(':')) : id);
        return this.reload().thenRun(() -> this.events.post(new SakuraOverridesChangedEvents
                .Messages(packet.change(), id, packet.version(), packet.actor(), locale,
                        true)));
    }

    @Override
    public List<MessageField> schema() {
        final List<MessageField> cached = this.schema.get();
        if (cached != null) {
            return cached;
        }
        final Map<String, String> english = MessageService.builtInTexts();
        final List<MessageField> fields = new ArrayList<>();
        for (final MessageKey key : MessageKey.all()) {
            final String text = english.getOrDefault(key.key(), "");
            fields.add(new SakuraSettings.Message(key.key(), placeholders(text), text,
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

    @Override
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
        return new SakuraSettings.Messages(this.version.get(), locale, texts, origins);
    }

    @Override
    public CompletableFuture<AdminResult<MessageSnapshot>> update(final Locale locale,
            final Map<String, String> patch, final long expectedVersion, final Actor actor) {
        final Map<String, @Nullable String> changes = new HashMap<>();
        final List<FieldError> errors = new ArrayList<>();
        patch.forEach((key, text) -> {
            if (!this.known(key, errors)) {
                return;
            }
            if (text == null || text.isBlank() || text.length() > MAX_TEXT) {
                errors.add(FieldError.of(key, FieldError.Code.INVALID,
                        "must be 1-" + MAX_TEXT + " characters"));
                return;
            }
            final String problem = this.parseProblem(key, text);
            if (problem != null) {
                errors.add(FieldError.of(key, FieldError.Code.INVALID_FORMAT, problem));
                return;
            }
            changes.put(key, text);
        });
        return this.change(locale, changes, errors, expectedVersion, actor);
    }

    @Override
    public CompletableFuture<AdminResult<MessageSnapshot>> reset(final Locale locale,
            final Set<String> keys, final long expectedVersion, final Actor actor) {
        final Map<String, @Nullable String> changes = new HashMap<>();
        final List<FieldError> errors = new ArrayList<>();
        for (final String key : keys) {
            if (this.known(key, errors)) {
                changes.put(key, null);
            }
        }
        return this.change(locale, changes, errors, expectedVersion, actor);
    }

    @Override
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

    private @Nullable String parseProblem(final String key, final String text) {
        final Set<String> allowed = new LinkedHashSet<>();
        allowed.add("prefix");
        for (final MessageField field : this.schema()) {
            if (field.key().equals(key)) {
                allowed.addAll(field.placeholders());
            }
        }
        final Matcher matcher = ANY_TAG.matcher(text);
        while (matcher.find()) {
            final String name = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!allowed.contains(name) && !TagResolver.standard().has(name)) {
                return "<" + name + "> is not a MiniMessage tag or a placeholder of this"
                        + " message (" + String.join(", ", allowed) + ")";
            }
        }
        return null;
    }

    private CompletableFuture<AdminResult<MessageSnapshot>> change(final Locale locale,
            final Map<String, @Nullable String> changes, final List<FieldError> errors,
            final long expectedVersion, final Actor actor) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.actions.check(checked, Action.Manage.of(Action.Manage.Area.MESSAGES))
                .thenCompose(decision -> {
                    if (!decision.allowed()) {
                        return CompletableFuture.completedFuture(
                                new SakuraAdminResult.Forbidden<>(decision));
                    }
                    if (!errors.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new SakuraAdminResult.ValidationFailed<>(errors));
                    }
                    return this.repository.changeMessages(locale, changes, expectedVersion,
                            checked, this.clock.instant()).thenCompose(written -> {
                                if (written.conflict()) {
                                    return CompletableFuture.completedFuture(
                                            new SakuraAdminResult.Conflict<>(
                                                    written.version()));
                                }
                                return this.reload().thenApply(ignored -> {
                                    final String id = locale.toLanguageTag();
                                    this.sync.publish(new SyncPacket.EntityChangedPacket(
                                            SubjectType.MESSAGE, id, ChangeType.UPDATED,
                                            written.version(), checked));
                                    this.events.post(new SakuraOverridesChangedEvents.Messages(
                                            ChangeType.UPDATED, id, written.version(), checked,
                                            locale, false));
                                    return new SakuraAdminResult.Ok<>(this.messages(locale));
                                });
                            });
                });
    }
}
