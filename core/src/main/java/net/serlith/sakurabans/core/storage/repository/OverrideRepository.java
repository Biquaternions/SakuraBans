package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbMetaRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbMessageOverrides;
import net.serlith.sakurabans.core.storage.dbo.tables.SbSettingOverrides;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

public final class OverrideRepository {

    static final String SETTINGS_VERSION = "settings.version";
    static final String MESSAGES_VERSION = "messages.version";

    private final StorageService storage;

    public OverrideRepository(final StorageService storage) {
        this.storage = storage;
    }

    public record Settings(long version, Map<String, String> values) {

        public Settings {
            values = Map.copyOf(values);
        }
    }

    public record Messages(long version, Map<Locale, Map<String, String>> texts) {

        public Messages {
            texts = Map.copyOf(texts);
        }
    }

    public record Written(boolean conflict, long version) {
    }

    public CompletableFuture<Settings> settings() {
        final SbSettingOverrides table = SakuraTables.SETTING_OVERRIDES;
        return this.storage.read(dsl -> {
            final Map<String, String> values = new HashMap<>();
            dsl.selectFrom(table).fetch().forEach(row -> values.put(row.name(), row.value()));
            return new Settings(version(dsl, SETTINGS_VERSION), values);
        });
    }

    public CompletableFuture<Messages> messages() {
        final SbMessageOverrides table = SakuraTables.MESSAGE_OVERRIDES;
        return this.storage.read(dsl -> {
            final Map<Locale, Map<String, String>> texts = new HashMap<>();
            dsl.selectFrom(table).fetch().forEach(row -> texts.computeIfAbsent(
                    Locale.forLanguageTag(row.locale()), ignored -> new HashMap<>())
                    .put(row.name(), row.text()));
            return new Messages(version(dsl, MESSAGES_VERSION), texts);
        });
    }

    public CompletableFuture<Written> changeSettings(final Map<String, @Nullable String> changes,
            final long expectedVersion, final Actor actor, final Instant at) {
        final SbSettingOverrides table = SakuraTables.SETTING_OVERRIDES;
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final long version = lockVersion(dsl, SETTINGS_VERSION);
            if (version != expectedVersion) {
                return new Written(true, version);
            }
            final Map<String, String> old = new TreeMap<>();
            final Map<String, String> updated = new TreeMap<>();
            for (final Map.Entry<String, @Nullable String> change : changes.entrySet()) {
                final String key = change.getKey();
                final String previous = dsl.select(table.V).from(table).where(table.K.eq(key))
                        .fetchOne(table.V);
                dsl.deleteFrom(table).where(table.K.eq(key)).execute();
                if (change.getValue() != null) {
                    dsl.insertInto(table).set(table.K, key).set(table.V, change.getValue())
                            .set(table.UPDATED_AT, at).execute();
                }
                old.put(key, previous == null ? "(file)" : previous.strip());
                updated.put(key, change.getValue() == null ? "(file)"
                        : change.getValue().strip());
            }
            storeVersion(dsl, SETTINGS_VERSION, version + 1);
            AuditRepository.append(dsl, at, actor, AuditAction.UPDATE, SubjectType.SETTINGS,
                    "settings", old.toString(), updated.toString());
            return new Written(false, version + 1);
        });
    }

    public CompletableFuture<Written> changeMessages(final Locale locale,
            final Map<String, @Nullable String> changes, final long expectedVersion,
            final Actor actor, final Instant at) {
        final SbMessageOverrides table = SakuraTables.MESSAGE_OVERRIDES;
        final String tag = locale.toLanguageTag();
        return this.storage.writeRetryingConflicts(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final long version = lockVersion(dsl, MESSAGES_VERSION);
            if (version != expectedVersion) {
                return new Written(true, version);
            }
            for (final Map.Entry<String, @Nullable String> change : changes.entrySet()) {
                final String key = change.getKey();
                final String previous = dsl.select(table.TEXT).from(table)
                        .where(table.LOCALE.eq(tag)).and(table.K.eq(key)).fetchOne(table.TEXT);
                dsl.deleteFrom(table).where(table.LOCALE.eq(tag)).and(table.K.eq(key)).execute();
                if (change.getValue() != null) {
                    dsl.insertInto(table).set(table.LOCALE, tag).set(table.K, key)
                            .set(table.TEXT, change.getValue()).set(table.UPDATED_AT, at)
                            .execute();
                }
                AuditRepository.append(dsl, at, actor, AuditAction.UPDATE, SubjectType.MESSAGE,
                        tag + ":" + key, previous, change.getValue());
            }
            storeVersion(dsl, MESSAGES_VERSION, version + 1);
            return new Written(false, version + 1);
        });
    }

    private static long version(final DSLContext dsl, final String key) {
        final String value = dsl.select(SakuraTables.META.V).from(SakuraTables.META)
                .where(SakuraTables.META.K.eq(key)).fetchOne(SakuraTables.META.V);
        return value == null ? 1L : Long.parseLong(value);
    }

    private static long lockVersion(final DSLContext dsl, final String key) {
        final SbMetaRecord row = dsl.selectFrom(SakuraTables.META)
                .where(SakuraTables.META.K.eq(key)).forUpdate().fetchOne();
        if (row == null) {
            dsl.insertInto(SakuraTables.META).set(SakuraTables.META.K, key)
                    .set(SakuraTables.META.V, "1").execute();
            return 1L;
        }
        return Long.parseLong(row.value());
    }

    private static void storeVersion(final DSLContext dsl, final String key, final long version) {
        dsl.update(SakuraTables.META).set(SakuraTables.META.V, Long.toString(version))
                .where(SakuraTables.META.K.eq(key)).execute();
    }

}
