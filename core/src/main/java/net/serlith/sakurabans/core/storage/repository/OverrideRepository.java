package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.configuration.SettingsSchema;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbMetaRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbMessageOverrides;
import net.serlith.sakurabans.core.storage.dbo.tables.SbSettingOverrides;
import net.serlith.sakurabans.core.util.Scopes;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

public final class OverrideRepository {

    static final String SECRET = "(secret)";
    static final String SETTINGS_VERSION = "settings.version";
    /**
     * Followed by the locale's tag ({@code global}) or by the scope id, ':' and the tag: each
     * scope and locale has its own version, 0 until it has one.
     */
    static final String MESSAGES_VERSION = "messages.version.";

    private final StorageService storage;

    public OverrideRepository(final StorageService storage) {
        this.storage = storage;
    }

    /** One scope's setting overrides: key to YAML text. */
    public record ScopeSettings(long version, Map<String, String> values) {

        public ScopeSettings {
            values = Map.copyOf(values);
        }
    }

    /** Every scope with overrides or a version; {@code global} is always there. */
    public record Settings(Map<Scope, ScopeSettings> scopes) {

        public Settings {
            scopes = Map.copyOf(scopes);
        }

        public ScopeSettings scope(final Scope scope) {
            return this.scopes.getOrDefault(scope, new ScopeSettings(
                    scope instanceof GlobalScope ? 1 : 0, Map.of()));
        }
    }

    /** One scope's message overrides in one locale. */
    public record ScopeMessages(long version, Map<String, String> texts) {

        public ScopeMessages {
            texts = Map.copyOf(texts);
        }
    }

    /** Every scope and locale with overrides or a version. */
    public record Messages(Map<Scope, Map<Locale, ScopeMessages>> scopes) {

        public Messages {
            final Map<Scope, Map<Locale, ScopeMessages>> copy = new HashMap<>();
            scopes.forEach((scope, locales) -> copy.put(scope, Map.copyOf(locales)));
            scopes = Map.copyOf(copy);
        }
    }

    public record Written(boolean conflict, long version) {
    }

    public CompletableFuture<Settings> settings() {
        final SbSettingOverrides table = SakuraTables.SETTING_OVERRIDES;
        return this.storage.read(dsl -> {
            final Map<Integer, Scope> scopes = new HashMap<>();
            dsl.selectFrom(SakuraTables.SCOPES).fetch().forEach(row ->
                    scopes.put(row.id(), ScopeRepository.scope(row)));
            final Map<Scope, Map<String, String>> values = new HashMap<>();
            values.put(GlobalScope.INSTANCE, new HashMap<>());
            dsl.selectFrom(table).fetch().forEach(row -> values.computeIfAbsent(
                    scopes.get(row.scopeId()), ignored -> new HashMap<>())
                    .put(row.name(), row.value()));
            final Map<Scope, Long> versions = new HashMap<>();
            versions.put(GlobalScope.INSTANCE, version(dsl, SETTINGS_VERSION));
            for (final SbMetaRecord row : dsl.selectFrom(SakuraTables.META).fetch()) {
                if (row.name().startsWith(SETTINGS_VERSION + ".")) {
                    final Scope scope = scopes.get(Integer.parseInt(row.name()
                            .substring(SETTINGS_VERSION.length() + 1)));
                    if (scope != null) {
                        versions.put(scope, Long.parseLong(row.value()));
                    }
                }
            }
            final Map<Scope, ScopeSettings> all = new HashMap<>();
            versions.forEach((scope, version) -> all.put(scope, new ScopeSettings(version,
                    values.getOrDefault(scope, Map.of()))));
            values.forEach((scope, texts) -> all.putIfAbsent(scope,
                    new ScopeSettings(0, texts)));
            return new Settings(all);
        });
    }

    public CompletableFuture<Messages> messages() {
        final SbMessageOverrides table = SakuraTables.MESSAGE_OVERRIDES;
        return this.storage.read(dsl -> {
            final Map<Integer, Scope> scopes = new HashMap<>();
            dsl.selectFrom(SakuraTables.SCOPES).fetch().forEach(row ->
                    scopes.put(row.id(), ScopeRepository.scope(row)));
            final Map<Scope, Map<Locale, Map<String, String>>> texts = new HashMap<>();
            dsl.selectFrom(table).fetch().forEach(row -> texts
                    .computeIfAbsent(scopes.get(row.scopeId()), ignored -> new HashMap<>())
                    .computeIfAbsent(Locale.forLanguageTag(row.locale()),
                            ignored -> new HashMap<>())
                    .put(row.name(), row.text()));
            final Map<Scope, Map<Locale, Long>> versions = new HashMap<>();
            for (final SbMetaRecord row : dsl.selectFrom(SakuraTables.META).fetch()) {
                if (!row.name().startsWith(MESSAGES_VERSION)) {
                    continue;
                }
                final String rest = row.name().substring(MESSAGES_VERSION.length());
                final int colon = rest.indexOf(':');
                final Scope scope = colon < 0 ? GlobalScope.INSTANCE
                        : scopes.get(Integer.parseInt(rest.substring(0, colon)));
                if (scope != null) {
                    versions.computeIfAbsent(scope, ignored -> new HashMap<>()).put(
                            Locale.forLanguageTag(rest.substring(colon + 1)),
                            Long.parseLong(row.value()));
                }
            }
            final Map<Scope, Map<Locale, ScopeMessages>> all = new HashMap<>();
            versions.forEach((scope, locales) -> locales.forEach((locale, version) -> all
                    .computeIfAbsent(scope, ignored -> new HashMap<>()).put(locale,
                            new ScopeMessages(version, texts.getOrDefault(scope, Map.of())
                                    .getOrDefault(locale, Map.of())))));
            texts.forEach((scope, locales) -> locales.forEach((locale, values) -> all
                    .computeIfAbsent(scope, ignored -> new HashMap<>())
                    .putIfAbsent(locale, new ScopeMessages(0, values))));
            return new Messages(all);
        });
    }

    /** Changes one scope's overrides, under that scope's own version. */
    public CompletableFuture<Written> changeSettings(final Scope scope,
            final Map<String, @Nullable String> changes, final long expectedVersion,
            final Actor actor, final Instant at) {
        final SbSettingOverrides table = SakuraTables.SETTING_OVERRIDES;
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final int scopeId = ScopeRepository.resolve(dsl, scope);
            final String versionKey = scope instanceof GlobalScope ? SETTINGS_VERSION
                    : SETTINGS_VERSION + "." + scopeId;
            final long version = lockVersion(dsl, versionKey,
                    scope instanceof GlobalScope ? 1 : 0);
            if (version != expectedVersion) {
                return new Written(true, version);
            }
            final Map<String, String> old = new TreeMap<>();
            final Map<String, String> updated = new TreeMap<>();
            for (final Map.Entry<String, @Nullable String> change : changes.entrySet()) {
                final String key = change.getKey();
                final String previous = dsl.select(table.V).from(table)
                        .where(table.SCOPE_ID.eq(scopeId)).and(table.K.eq(key))
                        .fetchOne(table.V);
                dsl.deleteFrom(table).where(table.SCOPE_ID.eq(scopeId)).and(table.K.eq(key))
                        .execute();
                if (change.getValue() != null) {
                    dsl.insertInto(table).set(table.SCOPE_ID, scopeId).set(table.K, key)
                            .set(table.V, change.getValue()).set(table.UPDATED_AT, at)
                            .execute();
                }
                // A secret's value never reaches the audit log.
                final boolean secret = SettingsSchema.secret(key);
                old.put(key, previous == null ? "(file)" : secret ? SECRET : previous.strip());
                updated.put(key, change.getValue() == null ? "(file)" : secret ? SECRET
                        : change.getValue().strip());
            }
            storeVersion(dsl, versionKey, version + 1);
            AuditRepository.append(dsl, at, actor, AuditAction.UPDATE, SubjectType.SETTINGS,
                    "settings " + Scopes.format(scope), old.toString(), updated.toString());
            return new Written(false, version + 1);
        });
    }

    /** Changes one scope's overrides in one locale, under their own version. */
    public CompletableFuture<Written> changeMessages(final Scope scope, final Locale locale,
            final Map<String, @Nullable String> changes, final long expectedVersion,
            final Actor actor, final Instant at) {
        final SbMessageOverrides table = SakuraTables.MESSAGE_OVERRIDES;
        final String tag = locale.toLanguageTag();
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final int scopeId = ScopeRepository.resolve(dsl, scope);
            final String versionKey = MESSAGES_VERSION
                    + (scope instanceof GlobalScope ? "" : scopeId + ":") + tag;
            final long version = lockVersion(dsl, versionKey, 0);
            if (version != expectedVersion) {
                return new Written(true, version);
            }
            final String subject = (scope instanceof GlobalScope ? ""
                    : Scopes.format(scope) + " ") + tag + ":";
            for (final Map.Entry<String, @Nullable String> change : changes.entrySet()) {
                final String key = change.getKey();
                final String previous = dsl.select(table.TEXT).from(table)
                        .where(table.SCOPE_ID.eq(scopeId)).and(table.LOCALE.eq(tag))
                        .and(table.K.eq(key)).fetchOne(table.TEXT);
                dsl.deleteFrom(table).where(table.SCOPE_ID.eq(scopeId))
                        .and(table.LOCALE.eq(tag)).and(table.K.eq(key)).execute();
                if (change.getValue() != null) {
                    dsl.insertInto(table).set(table.SCOPE_ID, scopeId).set(table.LOCALE, tag)
                            .set(table.K, key).set(table.TEXT, change.getValue())
                            .set(table.UPDATED_AT, at).execute();
                }
                AuditRepository.append(dsl, at, actor, AuditAction.UPDATE, SubjectType.MESSAGE,
                        subject + key, previous, change.getValue());
            }
            storeVersion(dsl, versionKey, version + 1);
            return new Written(false, version + 1);
        });
    }

    private static long version(final DSLContext dsl, final String key) {
        final String value = dsl.select(SakuraTables.META.V).from(SakuraTables.META)
                .where(SakuraTables.META.K.eq(key)).fetchOne(SakuraTables.META.V);
        return value == null ? 1L : Long.parseLong(value);
    }

    private static long lockVersion(final DSLContext dsl, final String key, final long first) {
        final SbMetaRecord row = dsl.selectFrom(SakuraTables.META)
                .where(SakuraTables.META.K.eq(key)).forUpdate().fetchOne();
        if (row == null) {
            dsl.insertInto(SakuraTables.META).set(SakuraTables.META.K, key)
                    .set(SakuraTables.META.V, Long.toString(first)).execute();
            return first;
        }
        return Long.parseLong(row.value());
    }

    private static void storeVersion(final DSLContext dsl, final String key, final long version) {
        dsl.update(SakuraTables.META).set(SakuraTables.META.V, Long.toString(version))
                .where(SakuraTables.META.K.eq(key)).execute();
    }

}
