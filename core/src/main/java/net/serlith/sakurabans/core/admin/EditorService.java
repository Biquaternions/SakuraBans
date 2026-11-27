package net.serlith.sakurabans.core.admin;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.ConfigIssue;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.SettingsSchema;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.StoredTemplate;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.storage.repository.EditorSessionRepository;
import net.serlith.sakurabans.core.storage.repository.InstanceRepository;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import net.serlith.sakurabans.core.util.BuildConstants;
import net.serlith.sakurabans.core.util.Durations;
import net.serlith.sakurabans.core.util.Scopes;
import org.jspecify.annotations.Nullable;

/** {@code /sb editor} and {@code /sb applyedits} (protocol sections 2, 5-7). */
public final class EditorService {

    public static final Duration SESSION_LIFETIME = Duration.ofHours(24);
    public static final String SOURCE = "web";
    private static final Pattern LOCALE = Pattern.compile("[a-z]{2,8}(_[A-Za-z0-9]{2,8})?");
    private static final String GLOBAL = "global";

    public enum Section {
        SETTINGS("settings", Permissions.ADMIN_SETTINGS),
        MESSAGES("messages", Permissions.ADMIN_MESSAGES),
        TEMPLATES("templates", Permissions.ADMIN_TEMPLATES);

        private final String id;
        private final String permission;

        Section(final String id, final String permission) {
            this.id = id;
            this.permission = permission;
        }

        public String id() {
            return this.id;
        }

        /** The node that lets a sender edit the section, here and through the commands. */
        public String permission() {
            return this.permission;
        }
    }

    public sealed interface Opened {

        record NotConfigured() implements Opened {
        }

        record Failed(EditorClient.Problem problem) implements Opened {
        }

        /** {@code token} is the session's secret toward the site. */
        record Ready(URI link, Duration lifetime, String session, String token,
                Instant createdAt) implements Opened {

            @Override
            public String toString() {
                return "Ready[session=" + this.session + "]";
            }
        }
    }

    /** Who started an application: someone's command, or the watcher of an open editor. */
    public enum By {
        COMMAND,
        LIVE
    }

    /** {@code creator} is the session's, when the session was found. */
    public record Application(Applied applied, @Nullable Operator creator) {
    }

    public sealed interface Applied {

        record NotConfigured() implements Applied {
        }

        /** The code is unknown or expired, or the site couldn't be asked. */
        record Failed(EditorClient.Problem problem) implements Applied {
        }

        /** The session isn't one of this network, or it expired. */
        record UnknownSession() implements Applied {
        }

        record Unreadable() implements Applied {
        }

        record OtherFormat(int format) implements Applied {
        }

        record Invalid(List<FieldError> errors) implements Applied {

            public Invalid {
                errors = List.copyOf(errors);
            }
        }

        record Forbidden(List<Section> sections) implements Applied {

            public Forbidden {
                sections = List.copyOf(sections);
            }
        }

        /** This change set, or a later one of the session, was applied already. */
        record Replayed() implements Applied {
        }

        record Done(List<Change> changes) implements Applied {

            public Done {
                changes = List.copyOf(changes);
            }

            public int applied() {
                return (int) this.changes.stream().filter(change -> !(change
                        instanceof Change.Unchanged) && !(change instanceof Change.Conflict))
                        .count();
            }

            public int unchanged() {
                return (int) this.changes.stream().filter(Change.Unchanged.class::isInstance)
                        .count();
            }

            public int conflicts() {
                return (int) this.changes.stream().filter(Change.Conflict.class::isInstance)
                        .count();
            }
        }
    }

    /**
     * One line of the reply and one entry of the site's result, in change-set order. Values are
     * plain trees.
     */
    public sealed interface Change {

        Section section();

        /** The scope as written ({@code global}, {@code server:lobby}); a template's variant. */
        String scope();

        /** The key, {@code <locale>:<key>} or the template name. */
        String target();

        record SettingSet(String scope, String key, @Nullable Object before,
                @Nullable Object after) implements Change {

            @Override
            public Section section() {
                return Section.SETTINGS;
            }

            @Override
            public String target() {
                return this.key;
            }
        }

        record SettingReset(String scope, String key, @Nullable Object now) implements Change {

            @Override
            public Section section() {
                return Section.SETTINGS;
            }

            @Override
            public String target() {
                return this.key;
            }
        }

        /** A key published from a server's files to the global scope. */
        record SettingPublished(String key, String server, @Nullable Object before,
                @Nullable Object after) implements Change {

            @Override
            public Section section() {
                return Section.SETTINGS;
            }

            @Override
            public String scope() {
                return GLOBAL;
            }

            @Override
            public String target() {
                return this.key;
            }
        }

        record MessageSet(String scope, String locale, String key, String text)
                implements Change {

            @Override
            public Section section() {
                return Section.MESSAGES;
            }

            @Override
            public String target() {
                return this.locale + ":" + this.key;
            }
        }

        record MessageReset(String scope, String locale, String key) implements Change {

            @Override
            public Section section() {
                return Section.MESSAGES;
            }

            @Override
            public String target() {
                return this.locale + ":" + this.key;
            }
        }

        record TemplateCreated(String scope, String target) implements Change {

            @Override
            public Section section() {
                return Section.TEMPLATES;
            }
        }

        record TemplateUpdated(String scope, String target, List<FieldChange> fields)
                implements Change {

            public TemplateUpdated {
                fields = List.copyOf(fields);
            }

            @Override
            public Section section() {
                return Section.TEMPLATES;
            }
        }

        record TemplateDeleted(String scope, String target) implements Change {

            @Override
            public Section section() {
                return Section.TEMPLATES;
            }
        }

        record Unchanged(Section section, String scope, String target) implements Change {
        }

        /** Not applied; {@code detail} says why for {@link Why#REFUSED}. */
        record Conflict(Section section, String scope, String target, Why why,
                @Nullable String detail) implements Change {
        }

        enum Why {
            /** Changed meanwhile (another version, or gone, or the name taken). */
            CHANGED,
            /** The actor may no longer change it. */
            FORBIDDEN,
            /** The service refused it when applied. */
            REFUSED
        }
    }

    /** {@code before} and {@code after} only for scalar fields. */
    public record FieldChange(String field, @Nullable Object before, @Nullable Object after) {
    }

    private final ConfigService config;
    private final SakuraSettingsService settings;
    private final SakuraMessageTemplateService messages;
    private final SakuraTemplateService templates;
    private final EditorSessionRepository sessions;
    private final EditorClient client;
    private final EditorSeal seal;
    private final Platform platform;
    private final InstanceRegistry registry;
    private final InstantSource clock;
    private final ComponentLogger logger;

    public EditorService(final ConfigService config, final SakuraSettingsService settings,
            final SakuraMessageTemplateService messages, final SakuraTemplateService templates,
            final EditorSessionRepository sessions, final EditorClient client,
            final EditorSeal seal, final Platform platform, final InstanceRegistry registry,
            final InstantSource clock, final ComponentLogger logger) {
        this.config = config;
        this.settings = settings;
        this.messages = messages;
        this.templates = templates;
        this.sessions = sessions;
        this.client = client;
        this.seal = seal;
        this.platform = platform;
        this.registry = registry;
        this.clock = clock;
        this.logger = logger;
    }

    public boolean configured() {
        return this.config.settings().web().url() != null;
    }

    public static Set<Section> allowed(final Predicate<String> permission) {
        final Set<Section> allowed = EnumSet.noneOf(Section.class);
        for (final Section section : Section.values()) {
            if (permission.test(section.permission())) {
                allowed.add(section);
            }
        }
        return allowed;
    }

    // Opening

    public CompletableFuture<Opened> open(final Actor actor, final Set<Section> sections) {
        final URI base = this.config.settings().web().url();
        if (base == null) {
            return CompletableFuture.completedFuture(new Opened.NotConfigured());
        }
        final Instant now = this.clock.instant();
        final byte[] key = this.seal.newKey();
        final byte[] blob = this.seal.seal(key, EditorSeal.Role.PAYLOAD,
                EditorJson.GSON.toJson(this.payload(actor.operator(), sections, now).json()));
        return this.client.upload(blob).thenCompose(response -> switch (response) {
            case EditorClient.Response.Failed<EditorClient.Uploaded> failed -> CompletableFuture
                    .completedFuture(new Opened.Failed(failed.problem()));
            case EditorClient.Response.Ok<EditorClient.Uploaded> ok -> this.sessions.create(
                            ok.value().id(), key, ok.value().token(), ids(sections),
                            actor.operator(), now, now.minus(SESSION_LIFETIME))
                    .thenApply(ignored -> new Opened.Ready(URI.create(base + "/editor/"
                            + ok.value().id() + "#" + Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(key)), SESSION_LIFETIME, ok.value().id(),
                            ok.value().token(), now));
        });
    }

    public EditorPayload payload(final Operator uploader, final Set<Section> sections,
            final Instant now) {
        final List<String> ids = new ArrayList<>();
        for (final Section section : Section.values()) {
            if (sections.contains(section)) {
                ids.add(section.id());
            }
        }
        final String server = this.config.settings().server().name();
        final EditorPayload.Meta meta = new EditorPayload.Meta(BuildConstants.VERSION,
                this.platform.platformName(), server == null ? "" : server, name(uploader),
                uploader instanceof PlayerOperator player ? player.uuid() : null,
                now.toEpochMilli(), ids);
        return new EditorPayload(meta,
                sections.contains(Section.SETTINGS) ? this.settingsSection() : null,
                sections.contains(Section.MESSAGES) ? this.messagesSection() : null,
                sections.contains(Section.TEMPLATES) ? this.templatesSection() : null,
                this.lookups());
    }

    private static String name(final Operator operator) {
        return switch (operator) {
            case ConsoleOperator console -> "Console";
            case PlayerOperator player -> player.name();
            case CustomOperator custom -> custom.name();
        };
    }

    private EditorPayload.Settings settingsSection() {
        final List<EditorPayload.Setting> fields = new ArrayList<>();
        for (final SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            if (entry.editable()) {
                fields.add(new EditorPayload.Setting(entry.key(), entry.group(), entry.type(),
                        entry.description(), entry.options(), entry.min(), entry.max(),
                        this.settings.plainDefault(entry.key()).orElse(null),
                        entry.level().name(), entry.secret()));
            }
        }
        final List<EditorPayload.SettingScope> scopes = new ArrayList<>();
        scopes.add(this.settingScope(GlobalScope.INSTANCE));
        this.settings.allOverrides().entrySet().stream()
                .filter(entry -> !(entry.getKey() instanceof GlobalScope)
                        && !entry.getValue().isEmpty())
                .map(Map.Entry::getKey).sorted(Comparator.comparing(Scopes::format))
                .forEach(scope -> scopes.add(this.settingScope(scope)));
        final List<EditorPayload.Server> servers = new ArrayList<>();
        final InstanceRepository.Instance self = this.registry.self();
        servers.add(server(self));
        for (final InstanceRepository.Instance instance : this.registry.instances()) {
            if (!instance.id().equals(self.id())) {
                servers.add(server(instance));
            }
        }
        return new EditorPayload.Settings(fields, scopes, servers);
    }

    private EditorPayload.SettingScope settingScope(final Scope scope) {
        final Map<String, @Nullable Object> overrides = new TreeMap<>();
        this.settings.overrides(scope).forEach((key, text) ->
                overrides.put(key, masked(key, this.settings.decoded(text))));
        return new EditorPayload.SettingScope(Scopes.format(scope), this.settings.version(scope),
                overrides);
    }

    private static EditorPayload.Server server(final InstanceRepository.Instance instance) {
        final Map<String, @Nullable Object> values = new LinkedHashMap<>();
        InstanceRegistry.fileValues(instance).forEach((key, value) ->
                values.put(key, masked(key, value)));
        return new EditorPayload.Server(instance.id(), instance.serverName(),
                instance.categories(), instance.platform(), instance.pluginVersion(),
                instance.lastSeenAt().toEpochMilli(), values);
    }

    /** A secret travels as whether it is set (protocol 5.2). */
    private static @Nullable Object masked(final String key, final @Nullable Object value) {
        return SettingsSchema.secret(key) ? value != null && !String.valueOf(value).isEmpty()
                : value;
    }

    private EditorPayload.Messages messagesSection() {
        final List<EditorPayload.Message> fields = new ArrayList<>();
        for (final MessageField field : this.messages.schema()) {
            fields.add(new EditorPayload.Message(field.key(), field.description(),
                    field.placeholders(), field.defaultText()));
        }
        final List<EditorPayload.LocaleTexts> locales = new ArrayList<>();
        for (final Locale locale : this.messages.locales()) {
            final Map<String, String> texts = new LinkedHashMap<>();
            for (final MessageField field : this.messages.schema()) {
                texts.put(field.key(), this.messages.fileText(field.key(), locale));
            }
            locales.add(new EditorPayload.LocaleTexts(locale.toString(), texts));
        }
        final List<EditorPayload.MessageScope> scopes = new ArrayList<>();
        this.messages.allOverrides().forEach((scope, byLocale) -> byLocale.forEach(
                (locale, texts) -> {
                    if (!texts.isEmpty()) {
                        scopes.add(new EditorPayload.MessageScope(Scopes.format(scope),
                                locale.toString(), this.messages.version(scope, locale),
                                new TreeMap<>(texts)));
                    }
                }));
        scopes.sort(Comparator.comparing((EditorPayload.MessageScope scope) ->
                        !scope.scope().equals(GLOBAL))
                .thenComparing(EditorPayload.MessageScope::scope)
                .thenComparing(EditorPayload.MessageScope::locale));
        return new EditorPayload.Messages(fields, locales, scopes);
    }

    private EditorPayload.Templates templatesSection() {
        final List<EditorPayload.Template> items = new ArrayList<>();
        // By name, the global definition first, then its variants (S62).
        for (final StoredTemplate template : this.templates.definitions()) {
            items.add(new EditorPayload.Template(Scopes.format(template.variant()
                    .orElse(GlobalScope.INSTANCE)), template.name(), template.version(),
                    TemplateFiles.tree(template.template())));
        }
        return new EditorPayload.Templates(items);
    }

    private EditorPayload.Lookups lookups() {
        final var settings = this.config.settings();
        final Set<String> servers = new TreeSet<>(this.platform.serverIdentity().backends());
        settings.scopes().categories().values().forEach(servers::addAll);
        if (settings.server().name() != null) {
            servers.add(settings.server().name());
        }
        final Set<String> categories = new TreeSet<>(settings.scopes().categories().keySet());
        categories.addAll(settings.server().categories());
        for (final InstanceRepository.Instance instance : this.registry.instances()) {
            if (!instance.serverName().isEmpty()) {
                servers.add(instance.serverName());
            }
            categories.addAll(instance.categories());
        }
        return new EditorPayload.Lookups(List.copyOf(servers), List.copyOf(categories),
                this.messages.locales().stream().map(Locale::toString).toList());
    }

    // Applying

    public CompletableFuture<Application> apply(final String code, final Actor actor,
            final Set<Section> allowed, final By by) {
        if (!this.configured()) {
            return CompletableFuture.completedFuture(new Application(new Applied.NotConfigured(),
                    null));
        }
        return this.client.changes(code).thenCompose(response -> switch (response) {
            case EditorClient.Response.Failed<EditorClient.Download> failed -> CompletableFuture
                    .completedFuture(new Application(new Applied.Failed(failed.problem()), null));
            case EditorClient.Response.Ok<EditorClient.Download> ok -> this.sessions
                    .find(ok.value().session())
                    .thenCompose(session -> session
                            .filter(found -> found.createdAt().isAfter(
                                    this.clock.instant().minus(SESSION_LIFETIME)))
                            .map(found -> this.apply(found, code, ok.value().blob(), actor,
                                    allowed, by).thenApply(applied -> new Application(applied,
                                            found.creator())))
                            .orElseGet(() -> CompletableFuture.completedFuture(new Application(
                                    new Applied.UnknownSession(), null))));
        });
    }

    private CompletableFuture<Applied> apply(final EditorSessionRepository.Session session,
            final String code, final byte[] blob, final Actor actor, final Set<Section> allowed,
            final By by) {
        final String json;
        try {
            json = EditorSeal.open(session.secret(), EditorSeal.Role.CHANGES, blob);
        } catch (final EditorSeal.SealException e) {
            return this.refused(session, code, 0, by, new Applied.Unreadable(),
                    List.of(invalid("", "the changes can't be read")));
        }
        final EditorChanges changes;
        switch (EditorChanges.parse(json)) {
            case EditorChanges.Parsed.OtherFormat other -> {
                return this.refused(session, code, 0, by, new Applied.OtherFormat(other.format()),
                        List.of(invalid("format", "is " + other.format() + "; this server reads "
                                + EditorPayload.FORMAT)));
            }
            case EditorChanges.Parsed.Invalid invalid -> {
                return this.refused(session, code, 0, by, new Applied.Invalid(invalid.errors()),
                        invalid.errors());
            }
            case EditorChanges.Parsed.Read read -> changes = read.changes();
        }
        if (changes.sequence() <= session.lastSequence()) {
            return CompletableFuture.completedFuture(new Applied.Replayed());
        }
        final Checked checked = this.check(changes);
        if (!checked.errors().isEmpty()) {
            return this.refused(session, code, changes.sequence(), by,
                    new Applied.Invalid(checked.errors()), checked.errors());
        }
        final List<Section> forbidden = new ArrayList<>();
        for (final Section section : checked.sections()) {
            if (!allowed.contains(section)) {
                forbidden.add(section);
            }
        }
        if (!forbidden.isEmpty()) {
            return this.refused(session, code, changes.sequence(), by,
                    new Applied.Forbidden(forbidden), forbidden.stream().map(section ->
                            invalid(section.id(), "may not be changed by this sender")).toList());
        }
        // Claimed before anything changes: of two instances applying one code, one goes on.
        return this.sessions.claim(session.id(), changes.sequence()).thenCompose(claimed -> {
            if (!claimed) {
                return CompletableFuture.completedFuture(new Applied.Replayed());
            }
            return new Run(Actor.of(actor.operator(), SOURCE)).all(checked)
                    .thenCompose(done -> this.refresh(session, allowed).thenCompose(revision ->
                            this.post(session, code, new EditorResult(changes.sequence(), true,
                                    by == By.LIVE, done.changes().stream()
                                            .map(EditorService::entry).toList(), List.of(),
                                    done.applied(), done.unchanged(), done.conflicts(),
                                    revision)))
                            .thenApply(ignored -> done));
        });
    }

    private CompletableFuture<Applied> refused(final EditorSessionRepository.Session session,
            final String code, final long sequence, final By by, final Applied applied,
            final List<FieldError> errors) {
        return this.post(session, code, new EditorResult(sequence, false, by == By.LIVE,
                List.of(), errors, 0, 0, 0, null)).thenApply(ignored -> applied);
    }

    /**
     * Uploads a fresh payload for the session's creator before the result (protocol section
     * 10): the sections the creator may edit now. A player creator who isn't online here gets
     * the session's sections that the applying sender may edit, so a snapshot never shows what
     * neither may see. Null when it couldn't be built or uploaded; the apply stands either way.
     */
    private CompletableFuture<@Nullable Long> refresh(
            final EditorSessionRepository.Session session, final Set<Section> sender) {
        final Set<Section> sections;
        final byte[] blob;
        try {
            sections = this.creatorSections(session, sender);
            blob = this.seal.seal(session.secret(), EditorSeal.Role.PAYLOAD,
                    EditorJson.GSON.toJson(this.payload(session.creator(), sections,
                            this.clock.instant()).json()));
        } catch (final RuntimeException e) {
            this.logger.debug("The fresh payload of editor session {} couldn't be built: {}",
                    session.id(), e.toString());
            return CompletableFuture.completedFuture(null);
        }
        return this.client.replacePayload(session.id(), session.token(), blob)
                .handle((response, failure) -> {
                    if (response instanceof EditorClient.Response.Ok<Long> ok) {
                        return ok.value();
                    }
                    this.logger.debug("The editor site didn't take the fresh payload of session"
                            + " {}: {}", session.id(), failure != null ? failure.toString()
                            : response);
                    return null;
                });
    }

    private Set<Section> creatorSections(final EditorSessionRepository.Session session,
            final Set<Section> sender) {
        return switch (session.creator()) {
            case ConsoleOperator console -> EnumSet.allOf(Section.class);
            case PlayerOperator player -> this.platform.player(player.uuid())
                    .map(online -> allowed(online::hasPermission))
                    .orElseGet(() -> {
                        final Set<Section> opened = EnumSet.noneOf(Section.class);
                        for (final Section section : sender) {
                            if (session.sections().contains(section.id())) {
                                opened.add(section);
                            }
                        }
                        return opened;
                    });
            case CustomOperator custom -> EnumSet.noneOf(Section.class);
        };
    }

    private static Set<String> ids(final Set<Section> sections) {
        final Set<String> ids = new LinkedHashSet<>();
        sections.forEach(section -> ids.add(section.id()));
        return ids;
    }

    /** Never fails: the site not taking the result changes nothing here. */
    private CompletableFuture<Void> post(final EditorSessionRepository.Session session,
            final String code, final EditorResult result) {
        final byte[] blob = this.seal.seal(session.secret(), EditorSeal.Role.RESULT,
                EditorJson.GSON.toJson(result.json()));
        return this.client.postResult(code, session.token(), blob).handle((response, failure) -> {
            if (failure != null || response instanceof EditorClient.Response.Failed<?>) {
                this.logger.debug("The editor site didn't take the result for {}: {}", code,
                        failure != null ? failure.toString() : response);
            }
            return null;
        });
    }

    /** A change as the site's result shows it: plain text, never MiniMessage. */
    public static EditorResult.Entry entry(final Change change) {
        final String section = change.section().id();
        final String scope = change.scope();
        final String target = change.target();
        return switch (change) {
            // A secret's value is never in a result.
            case Change.SettingSet set -> new EditorResult.Entry(section, scope, target, "set",
                    SettingsSchema.secret(set.key()) ? ""
                            : text(set.before()) + " → " + text(set.after()));
            case Change.SettingReset reset -> new EditorResult.Entry(section, scope, target,
                    "reset", SettingsSchema.secret(reset.key()) ? "" : text(reset.now()));
            case Change.SettingPublished published -> new EditorResult.Entry(section, scope,
                    target, "published", SettingsSchema.secret(published.key()) ? ""
                            : text(published.before()) + " → " + text(published.after()));
            case Change.MessageSet set -> new EditorResult.Entry(section, scope, target, "set",
                    set.text());
            case Change.MessageReset reset -> new EditorResult.Entry(section, scope, target,
                    "reset", "");
            case Change.TemplateCreated created -> new EditorResult.Entry(section, scope,
                    target, "created", "");
            case Change.TemplateUpdated updated -> new EditorResult.Entry(section, scope,
                    target, "updated", String.join(", ", updated.fields().stream()
                            .map(FieldChange::field).toList()));
            case Change.TemplateDeleted deleted -> new EditorResult.Entry(section, scope,
                    target, "deleted", "");
            case Change.Unchanged unchanged -> new EditorResult.Entry(section, scope, target,
                    "unchanged", "");
            case Change.Conflict conflict -> new EditorResult.Entry(section, scope, target,
                    "conflict", switch (conflict.why()) {
                        case CHANGED -> "";
                        case FORBIDDEN -> "not allowed";
                        case REFUSED -> Objects.requireNonNullElse(conflict.detail(), "");
                    });
        };
    }

    /** A change set that passed validation, with values converted for the services. */
    private record Checked(List<FieldError> errors, Set<Section> sections,
            List<CheckedSettings> settings, List<CheckedLocale> messages,
            List<CheckedTemplate> templates) {
    }

    /** {@code published} are the values publish takes from the server's registry row. */
    private record CheckedSettings(Scope scope, long expectedVersion, Map<String, Object> set,
            Set<String> reset, @Nullable String server, Map<String, Object> published) {
    }

    private record CheckedLocale(Scope scope, Locale locale, long expectedVersion,
            Map<String, String> set, Set<String> reset) {
    }

    /** {@code variant}: null for the global definition. */
    private record CheckedTemplate(EditorChanges.TemplateChange change, @Nullable Scope variant,
            String name, @Nullable TemplateInfo definition) {

        /**
         * The order templates are applied in (S62): variant deletions, global creations and
         * updates, variant creations and updates, global deletions. So a variant meets its
         * global template, and a kind change can follow the removal of its variants.
         */
        int phase() {
            final boolean delete = this.change instanceof EditorChanges.TemplateChange.Delete;
            if (this.variant != null) {
                return delete ? 0 : 2;
            }
            return delete ? 3 : 1;
        }
    }

    private Checked check(final EditorChanges changes) {
        final List<FieldError> errors = new ArrayList<>();
        final Set<Section> sections = new LinkedHashSet<>();
        if (!changes.settings().isEmpty()) {
            sections.add(Section.SETTINGS);
        }
        final List<CheckedSettings> settings = this.checkSettings(changes.settings(), errors);
        final List<CheckedLocale> locales = new ArrayList<>();
        final Set<String> seenLocales = new HashSet<>();
        for (int i = 0; i < changes.messages().size(); i++) {
            sections.add(Section.MESSAGES);
            final EditorChanges.LocaleChange change = changes.messages().get(i);
            final String path = "messages[" + i + "]";
            final Scope scope = scope(change.scope(), path, errors);
            if (!LOCALE.matcher(change.locale()).matches()) {
                errors.add(invalid(path + ".locale", "is not a locale like en_US"));
                continue;
            }
            if (scope == null) {
                continue;
            }
            final Locale locale = Locale.forLanguageTag(change.locale().replace('_', '-'));
            if (!seenLocales.add(Scopes.format(scope) + " " + locale)) {
                errors.add(invalid(path, "repeats the scope and locale of another block"));
            }
            for (int j = 0; j < change.reset().size(); j++) {
                if (change.set().containsKey(change.reset().get(j))) {
                    errors.add(invalid(path + ".reset[" + j + "]", "is also set"));
                }
            }
            for (final FieldError error : this.messages.validate(change.set(),
                    Set.copyOf(change.reset()))) {
                errors.add(FieldError.of(change.set().containsKey(error.path())
                        ? path + ".set." + error.path()
                        : path + ".reset[" + change.reset().indexOf(error.path()) + "]",
                        error.code(), error.message()));
            }
            locales.add(new CheckedLocale(scope, locale, change.expectedVersion(), change.set(),
                    new LinkedHashSet<>(change.reset())));
        }
        final List<CheckedTemplate> templates = new ArrayList<>();
        final Set<String> seenNames = new HashSet<>();
        for (int i = 0; i < changes.templates().size(); i++) {
            sections.add(Section.TEMPLATES);
            final EditorChanges.TemplateChange change = changes.templates().get(i);
            final String path = "templates[" + i + "]";
            final Scope scope = scope(change.scope(), path, errors);
            final String name = Validator.normalizeIdentifier(change.name()).orElse(null);
            if (name == null) {
                errors.add(invalid(path + ".name", "must be 1-" + Validator.MAX_IDENTIFIER
                        + " characters of a-z, 0-9, '_' or '-'"));
                continue;
            }
            if (scope == null) {
                continue;
            }
            if (!seenNames.add(Scopes.format(scope) + " " + name)) {
                errors.add(invalid(path + ".name", "is changed twice"));
            }
            final Map<String, @Nullable Object> tree = switch (change) {
                case EditorChanges.TemplateChange.Create create -> create.definition();
                case EditorChanges.TemplateChange.Update update -> update.definition();
                case EditorChanges.TemplateChange.Delete delete -> null;
            };
            if (change instanceof EditorChanges.TemplateChange.Update update
                    && update.expectedVersion() < 1
                    || change instanceof EditorChanges.TemplateChange.Delete delete
                    && delete.expectedVersion() < 1) {
                errors.add(invalid(path + ".expectedVersion", "must be at least 1"));
            }
            TemplateInfo definition = null;
            if (tree != null) {
                final TemplateFiles.Read read = TemplateFiles.definition(name, tree,
                        path + ".definition", scope);
                for (final ConfigIssue issue : read.errors()) {
                    errors.add(invalid(issue.path(), issue.message()));
                }
                definition = read.templates().isEmpty() ? null : read.templates().getFirst();
            }
            templates.add(new CheckedTemplate(change,
                    scope instanceof GlobalScope ? null : scope, name, definition));
        }
        templates.sort(Comparator.comparingInt(CheckedTemplate::phase));
        return new Checked(errors, sections, settings, locales, templates);
    }

    /** A block's scope: global, a server or a category. */
    private static @Nullable Scope scope(final String text, final String path,
            final List<FieldError> errors) {
        final Scope scope = Scopes.parse(text).orElse(null);
        if (scope == null) {
            errors.add(invalid(path + ".scope",
                    "is not a scope (global, server:<name> or category:<name>)"));
        }
        return scope;
    }

    private List<CheckedSettings> checkSettings(final List<EditorChanges.SettingsChange> blocks,
            final List<FieldError> errors) {
        final int before = errors.size();
        final List<CheckedSettings> checked = new ArrayList<>();
        final List<String> paths = new ArrayList<>();
        final Set<Scope> seen = new HashSet<>();
        for (int i = 0; i < blocks.size(); i++) {
            final EditorChanges.SettingsChange change = blocks.get(i);
            final String path = "settings[" + i + "]";
            final Scope scope = scope(change.scope(), path, errors);
            if (scope == null) {
                continue;
            }
            if (!seen.add(scope)) {
                errors.add(invalid(path + ".scope", "is listed in another block too"));
            }
            final Map<String, Object> set = new LinkedHashMap<>();
            change.set().forEach((key, value) -> {
                final SettingsSchema.Entry entry = this.editable(key, path + ".set." + key,
                        errors);
                final Object converted = entry == null ? null
                        : value(entry, value, path + ".set." + key, errors);
                if (converted != null) {
                    set.put(key, converted);
                }
            });
            for (int j = 0; j < change.reset().size(); j++) {
                if (change.set().containsKey(change.reset().get(j))) {
                    errors.add(invalid(path + ".reset[" + j + "]", "is also set"));
                }
            }
            String server = null;
            final Map<String, Object> published = new LinkedHashMap<>();
            if (change.publish() != null) {
                server = change.publish().server();
                this.checkPublish(scope, change, path, published, errors);
            }
            checked.add(new CheckedSettings(scope, change.expectedVersion(), set,
                    new LinkedHashSet<>(change.reset()), server, published));
            paths.add(path);
        }
        if (errors.size() > before) {
            return checked;
        }
        // Level, storage and whether the result still loads here, for all blocks together.
        final List<SakuraSettingsService.Block> together = new ArrayList<>();
        for (final CheckedSettings block : checked) {
            final Map<String, Object> set = new LinkedHashMap<>(block.set());
            set.putAll(block.published());
            together.add(new SakuraSettingsService.Block(block.scope(), set, block.reset()));
        }
        for (final SakuraSettingsService.BlockError error
                : this.settings.validateTogether(together)) {
            final String key = error.error().path();
            String path = "settings";
            if (error.block() >= 0) {
                final CheckedSettings block = checked.get(error.block());
                final EditorChanges.SettingsChange change = blocks.get(error.block());
                final String base = paths.get(error.block());
                path = block.set().containsKey(key) ? base + ".set." + key
                        : block.published().containsKey(key) ? base + ".publish.keys["
                                + Objects.requireNonNull(change.publish()).keys().indexOf(key)
                                + "]"
                        : base + ".reset[" + change.reset().indexOf(key) + "]";
            }
            errors.add(FieldError.of(path, error.error().code(), error.error().message()));
        }
        return checked;
    }

    private void checkPublish(final Scope scope, final EditorChanges.SettingsChange change,
            final String path, final Map<String, Object> published,
            final List<FieldError> errors) {
        final EditorChanges.Publish publish = Objects.requireNonNull(change.publish());
        if (!(scope instanceof GlobalScope)) {
            errors.add(invalid(path + ".publish", "is only allowed in the global block"));
            return;
        }
        if (publish.server().isEmpty()) {
            errors.add(invalid(path + ".publish.server", "names no server: a server without"
                    + " a server.name can't be published from"));
            return;
        }
        final InstanceRepository.Instance row = this.row(publish.server());
        if (row == null) {
            errors.add(invalid(path + ".publish.server", "is not a server of the registry"));
            return;
        }
        final Map<String, @Nullable Object> files = InstanceRegistry.fileValues(row);
        for (int j = 0; j < publish.keys().size(); j++) {
            final String key = publish.keys().get(j);
            final String keyPath = path + ".publish.keys[" + j + "]";
            if (this.editable(key, keyPath, errors) == null) {
                continue;
            }
            if (change.set().containsKey(key) || change.reset().contains(key)) {
                errors.add(invalid(keyPath, "is also set or reset"));
            } else if (files.get(key) == null) {
                errors.add(invalid(keyPath, "has no file value on " + publish.server()));
            } else {
                published.put(key, Objects.requireNonNull(files.get(key)));
            }
        }
    }

    private SettingsSchema.@Nullable Entry editable(final String key, final String path,
            final List<FieldError> errors) {
        final SettingsSchema.Entry entry = SettingsSchema.entry(key).orElse(null);
        if (entry == null || !entry.editable()) {
            errors.add(invalid(path, entry == null ? "is not a setting"
                    : "differs per server and is only set in its file"));
            return null;
        }
        return entry;
    }

    /** A registry row by server name; this server's own row is built fresh. */
    private InstanceRepository.@Nullable Instance row(final String name) {
        final InstanceRepository.Instance self = this.registry.self();
        if (!name.isEmpty() && self.serverName().equals(name)) {
            return self;
        }
        return this.registry.instances().stream()
                .filter(instance -> instance.serverName().equals(name)).findFirst()
                .orElse(null);
    }

    private InstanceRepository.@Nullable Instance row(final Scope scope) {
        return scope instanceof ServerScope server ? this.row(server.server()) : null;
    }

    /** The value as the configuration stores it, or null after reporting why it can't be. */
    private static @Nullable Object value(final SettingsSchema.Entry entry,
            final @Nullable Object value, final String path, final List<FieldError> errors) {
        final Object converted = switch (entry.type()) {
            case BOOLEAN -> value instanceof Boolean ? value : null;
            case INTEGER -> value instanceof Long ? value : null;
            case DECIMAL -> value instanceof Number number ? number.doubleValue() : null;
            case STRING -> value instanceof String ? value : null;
            case ENUM -> value instanceof String text && entry.options().contains(text)
                    ? text : null;
            case DURATION -> value instanceof String text
                    && (Durations.parse(text).isPresent() || Durations.isZero(text))
                    ? text : null;
            case STRING_LIST -> value instanceof List<?> list
                    && list.stream().allMatch(String.class::isInstance) ? list : null;
            case MAP -> value instanceof Map<?, ?> ? value : null;
        };
        if (converted == null) {
            errors.add(invalid(path, switch (entry.type()) {
                case BOOLEAN -> "must be true or false";
                case INTEGER -> "must be a whole number";
                case DECIMAL -> "must be a number";
                case STRING -> "must be a text";
                case ENUM -> "must be one of " + String.join(", ", entry.options());
                case DURATION -> "must be a duration like 30s, 1h30m or 7d";
                case STRING_LIST -> "must be a list of texts";
                case MAP -> "must be an object";
            }));
            return null;
        }
        if (converted instanceof Number number) {
            final double amount = number.doubleValue();
            if (entry.min() != null && amount < entry.min()
                    || entry.max() != null && amount > entry.max()) {
                errors.add(FieldError.of(path, FieldError.Code.OUT_OF_RANGE,
                        "must be " + range(entry)));
                return null;
            }
        }
        return converted;
    }

    private static String range(final SettingsSchema.Entry entry) {
        if (entry.min() != null && entry.max() != null) {
            return "from " + number(entry.min()) + " to " + number(entry.max());
        }
        return entry.min() != null ? "at least " + number(entry.min())
                : "at most " + number(Objects.requireNonNull(entry.max()));
    }

    private static String number(final double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static FieldError invalid(final String path, final String message) {
        return FieldError.of(path, FieldError.Code.INVALID, message);
    }

    /** One application, collecting its changes in change-set order. */
    private final class Run {

        private final Actor actor;
        private final List<Change> changes = new ArrayList<>();

        private Run(final Actor actor) {
            this.actor = actor;
        }

        /** Global settings first, then the other settings blocks, messages and templates. */
        private CompletableFuture<Applied.Done> all(final Checked checked) {
            final List<CheckedSettings> settings = new ArrayList<>(checked.settings());
            settings.sort(Comparator.comparing(block -> !(block.scope()
                    instanceof GlobalScope)));
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (final CheckedSettings block : settings) {
                chain = chain.thenCompose(ignored -> this.settings(block));
            }
            for (final CheckedLocale locale : checked.messages()) {
                chain = chain.thenCompose(ignored -> this.locale(locale));
            }
            for (final CheckedTemplate template : checked.templates()) {
                chain = chain.thenCompose(ignored -> this.template(template));
            }
            return chain.thenApply(ignored -> new Applied.Done(this.changes));
        }

        /** One write per block: publish and set/reset touch other keys, so order is moot. */
        private CompletableFuture<Void> settings(final CheckedSettings block) {
            final SakuraSettingsService service = EditorService.this.settings;
            final Scope scope = block.scope();
            final String name = Scopes.format(scope);
            final Map<String, Object> all = new LinkedHashMap<>(block.set());
            all.putAll(block.published());
            final Map<String, @Nullable String> effective =
                    service.effective(scope, all, block.reset());
            final List<String> keys = new ArrayList<>(block.set().keySet());
            keys.addAll(block.reset());
            keys.addAll(block.published().keySet());
            final Map<String, @Nullable Object> before = new LinkedHashMap<>();
            for (final String key : keys) {
                before.put(key, service.seen(scope, key, EditorService.this.row(scope)));
            }
            if (effective.isEmpty()) {
                keys.forEach(key -> this.changes.add(new Change.Unchanged(Section.SETTINGS,
                        name, key)));
                return CompletableFuture.completedFuture(null);
            }
            final Map<String, Object> set = new LinkedHashMap<>();
            all.forEach((key, value) -> {
                if (effective.containsKey(key)) {
                    set.put(key, value);
                }
            });
            final Set<String> reset = new LinkedHashSet<>(block.reset());
            reset.retainAll(effective.keySet());
            return service.change(scope, set, reset, block.expectedVersion(), this.actor)
                    .thenAccept(result -> {
                        final Failure failure = failure(result);
                        for (final String key : keys) {
                            final Object now = failure != null ? null
                                    : service.seen(scope, key, EditorService.this.row(scope));
                            if (failure != null) {
                                this.changes.add(failure.of(Section.SETTINGS, name, key));
                            } else if (!effective.containsKey(key)) {
                                this.changes.add(new Change.Unchanged(Section.SETTINGS, name,
                                        key));
                            } else if (block.published().containsKey(key)) {
                                this.changes.add(new Change.SettingPublished(key,
                                        Objects.requireNonNull(block.server()), before.get(key),
                                        now));
                            } else if (set.containsKey(key)) {
                                this.changes.add(new Change.SettingSet(name, key,
                                        before.get(key), now));
                            } else {
                                this.changes.add(new Change.SettingReset(name, key, now));
                            }
                        }
                    });
        }

        private CompletableFuture<Void> locale(final CheckedLocale change) {
            final SakuraMessageTemplateService service = EditorService.this.messages;
            final String name = Scopes.format(change.scope());
            final Map<String, @Nullable String> effective = service.effective(change.scope(),
                    change.locale(), change.set(), change.reset());
            final String tag = change.locale().toString();
            final List<String> keys = new ArrayList<>(change.set().keySet());
            keys.addAll(change.reset());
            if (effective.isEmpty()) {
                keys.forEach(key -> this.changes.add(new Change.Unchanged(Section.MESSAGES,
                        name, tag + ":" + key)));
                return CompletableFuture.completedFuture(null);
            }
            final Map<String, String> set = new LinkedHashMap<>();
            change.set().forEach((key, text) -> {
                if (effective.containsKey(key)) {
                    set.put(key, text);
                }
            });
            final Set<String> reset = new LinkedHashSet<>(change.reset());
            reset.retainAll(effective.keySet());
            return service.change(change.scope(), change.locale(), set, reset,
                    change.expectedVersion(), this.actor).thenAccept(result -> {
                        final Failure failure = failure(result);
                        for (final String key : keys) {
                            if (failure != null) {
                                this.changes.add(failure.of(Section.MESSAGES, name,
                                        tag + ":" + key));
                            } else if (!effective.containsKey(key)) {
                                this.changes.add(new Change.Unchanged(Section.MESSAGES, name,
                                        tag + ":" + key));
                            } else if (set.containsKey(key)) {
                                this.changes.add(new Change.MessageSet(name, tag, key,
                                        set.get(key)));
                            } else {
                                this.changes.add(new Change.MessageReset(name, tag, key));
                            }
                        }
                    });
        }

        private CompletableFuture<Void> template(final CheckedTemplate checked) {
            final SakuraTemplateService service = EditorService.this.templates;
            final String name = checked.name();
            final Scope variant = checked.variant();
            final String scope = Scopes.format(variant == null ? GlobalScope.INSTANCE : variant);
            final StoredTemplate current = service.find(name, variant).orElse(null);
            return switch (checked.change()) {
                case EditorChanges.TemplateChange.Create create -> {
                    if (current != null) {
                        this.conflict(scope, name);
                        yield CompletableFuture.completedFuture(null);
                    }
                    yield service.create(Objects.requireNonNull(checked.definition()),
                            this.actor).thenAccept(result -> {
                                if (result instanceof AdminResult.ValidationFailed<?>
                                        && variant == null) {
                                    // The name was taken meanwhile.
                                    this.conflict(scope, name);
                                } else if (!this.failed(result, scope, name)) {
                                    this.changes.add(new Change.TemplateCreated(scope, name));
                                }
                            });
                }
                case EditorChanges.TemplateChange.Update update -> service.update(
                        Objects.requireNonNull(checked.definition()), update.expectedVersion(),
                        this.actor).thenAccept(result -> {
                            if (this.failed(result, scope, name)) {
                                return;
                            }
                            final StoredTemplate after =
                                    ((AdminResult.Ok<StoredTemplate>) result).value();
                            if (current == null || after.version() == update.expectedVersion()) {
                                this.changes.add(new Change.Unchanged(Section.TEMPLATES, scope,
                                        name));
                                return;
                            }
                            this.changes.add(new Change.TemplateUpdated(scope, name,
                                    fields(TemplateFiles.tree(current.template()),
                                            TemplateFiles.tree(after.template()))));
                        });
                case EditorChanges.TemplateChange.Delete delete -> service.deleteWithVariants(
                        variant, name, delete.expectedVersion(), this.actor).thenAccept(result -> {
                            if (!this.failed(result, scope, name)) {
                                this.changes.add(new Change.TemplateDeleted(scope, name));
                                // A global template takes its variants with it.
                                ((AdminResult.Ok<SakuraTemplateService.Deletion>) result).value()
                                        .variants().forEach(gone -> this.changes.add(
                                                new Change.TemplateDeleted(Scopes.format(gone),
                                                        name)));
                            }
                        });
            };
        }

        /** Records a template's conflict or failure; false when the result is a success. */
        private boolean failed(final AdminResult<?> result, final String scope,
                final String name) {
            final Failure failure = failure(result);
            if (failure != null) {
                this.changes.add(failure.of(Section.TEMPLATES, scope, name));
            }
            return failure != null;
        }

        private void conflict(final String scope, final String name) {
            this.changes.add(new Change.Conflict(Section.TEMPLATES, scope, name,
                    Change.Why.CHANGED, null));
        }
    }

    private record Failure(Change.Why why, @Nullable String detail) {

        Change.Conflict of(final Section section, final String scope, final String target) {
            return new Change.Conflict(section, scope, target, this.why, this.detail);
        }
    }

    /** Null for a success. */
    private static @Nullable Failure failure(final AdminResult<?> result) {
        return switch (result) {
            case AdminResult.Ok<?> ok -> null;
            case AdminResult.Conflict<?> conflict -> new Failure(Change.Why.CHANGED, null);
            case AdminResult.NotFound<?> notFound -> new Failure(Change.Why.CHANGED, null);
            case AdminResult.Forbidden<?> forbidden -> new Failure(Change.Why.FORBIDDEN, null);
            case AdminResult.ValidationFailed<?> failed -> new Failure(Change.Why.REFUSED,
                    String.join("; ", failed.errors().stream()
                            .map(error -> error.path() + " " + error.message()).toList()));
        };
    }

    private static List<FieldChange> fields(final Map<String, Object> before,
            final Map<String, Object> after) {
        final Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        final List<FieldChange> fields = new ArrayList<>();
        for (final String key : keys) {
            final Object old = before.get(key);
            final Object now = after.get(key);
            if (!Objects.equals(old, now)) {
                fields.add(new FieldChange(key, scalar(old) ? old : null,
                        scalar(now) ? now : null));
            }
        }
        return fields;
    }

    /** A plain tree as short text: {@code {minigames: [bedwars-1, skywars]}}. */
    public static String text(final @Nullable Object value) {
        return switch (value) {
            case null -> "";
            case Map<?, ?> map -> "{" + String.join(", ", map.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + text(entry.getValue())).toList()) + "}";
            case Iterable<?> list -> {
                final List<String> items = new ArrayList<>();
                list.forEach(item -> items.add(text(item)));
                yield "[" + String.join(", ", items) + "]";
            }
            default -> String.valueOf(value);
        };
    }

    private static boolean scalar(final @Nullable Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Number;
    }
}
