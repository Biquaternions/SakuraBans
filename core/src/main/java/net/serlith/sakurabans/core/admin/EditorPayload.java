package net.serlith.sakurabans.core.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.core.model.SettingType;
import org.jspecify.annotations.Nullable;

/** What {@code /sb editor} uploads (protocol section 5). Absent sections are null. */
public record EditorPayload(Meta meta, @Nullable Settings settings, @Nullable Messages messages,
        @Nullable Templates templates, List<RecentChange> recentChanges, Lookups lookups) {

    public EditorPayload {
        recentChanges = List.copyOf(recentChanges);
    }

    public static final int FORMAT = 1;

    public record Meta(String pluginVersion, String platform, String server, String uploaderName,
            @Nullable UUID uploaderUuid, long createdAt, List<String> sections) {

        public Meta {
            sections = List.copyOf(sections);
        }
    }

    public record Settings(List<Setting> fields, List<SettingScope> scopes,
            List<Server> servers) {

        public Settings {
            fields = List.copyOf(fields);
            scopes = List.copyOf(scopes);
            servers = List.copyOf(servers);
        }
    }

    /** Values are plain trees (maps, lists, strings, numbers, booleans). */
    public record Setting(String key, String group, SettingType type, String description,
            List<String> options, @Nullable Double min, @Nullable Double max,
            @Nullable Object defaultValue, String level, boolean secret) {

        public Setting {
            options = List.copyOf(options);
        }
    }

    /** {@code overrides} hold {@code true}/{@code false} for secrets, never their value. */
    public record SettingScope(String scope, long version,
            Map<String, @Nullable Object> overrides) {

        public SettingScope {
            overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
        }
    }

    /** A server of the registry; secrets in {@code fileValues} are {@code true}/{@code false}. */
    public record Server(String id, String name, List<String> categories, String platform,
            String pluginVersion, long lastSeenAt, Map<String, @Nullable Object> fileValues) {

        public Server {
            categories = List.copyOf(categories);
            fileValues = Collections.unmodifiableMap(new LinkedHashMap<>(fileValues));
        }
    }

    public record Messages(List<Message> fields, List<LocaleTexts> locales,
            List<MessageScope> scopes) {

        public Messages {
            fields = List.copyOf(fields);
            locales = List.copyOf(locales);
            scopes = List.copyOf(scopes);
        }
    }

    public record Message(String key, String description, List<String> placeholders,
            String defaultText) {

        public Message {
            placeholders = List.copyOf(placeholders);
        }
    }

    /** {@code locale} in en_US form; the uploading server's file texts in key order. */
    public record LocaleTexts(String locale, Map<String, String> fileTexts) {

        public LocaleTexts {
            fileTexts = Collections.unmodifiableMap(new LinkedHashMap<>(fileTexts));
        }
    }

    public record MessageScope(String scope, String locale, long version,
            Map<String, String> overrides) {

        public MessageScope {
            overrides = Collections.unmodifiableMap(new LinkedHashMap<>(overrides));
        }
    }

    public record Templates(List<Template> items) {

        public Templates {
            items = List.copyOf(items);
        }
    }

    /**
     * {@code definition} is the tree {@code templates.yml} writes; {@code scope} is
     * {@code global} or the variant's scope (S62).
     */
    public record Template(String scope, String name, long version,
            Map<String, Object> definition) {
    }

    public record RecentChange(long at, String actor, String action, String kind, String scope,
            @Nullable String locale, String target, List<String> keys) {

        public RecentChange {
            keys = List.copyOf(keys);
        }
    }

    public record Lookups(List<String> servers, List<String> categories, List<String> locales) {

        public Lookups {
            servers = List.copyOf(servers);
            categories = List.copyOf(categories);
            locales = List.copyOf(locales);
        }
    }

    public JsonObject json() {
        final JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        final JsonObject meta = new JsonObject();
        meta.addProperty("pluginVersion", this.meta.pluginVersion());
        meta.addProperty("platform", this.meta.platform());
        meta.addProperty("server", this.meta.server());
        final JsonObject uploader = new JsonObject();
        uploader.addProperty("name", this.meta.uploaderName());
        uploader.add("uuid", this.meta.uploaderUuid() == null ? JsonNull.INSTANCE
                : new JsonPrimitive(this.meta.uploaderUuid().toString()));
        meta.add("uploader", uploader);
        meta.addProperty("createdAt", this.meta.createdAt());
        meta.add("sections", EditorJson.json(this.meta.sections()));
        root.add("meta", meta);
        if (this.settings != null) {
            final JsonObject settings = new JsonObject();
            final JsonArray fields = new JsonArray();
            for (final Setting setting : this.settings.fields()) {
                final JsonObject field = new JsonObject();
                field.addProperty("key", setting.key());
                field.addProperty("group", setting.group());
                field.addProperty("type", setting.type().name());
                field.addProperty("description", setting.description());
                field.add("options", EditorJson.json(setting.options()));
                field.add("min", EditorJson.json(setting.min()));
                field.add("max", EditorJson.json(setting.max()));
                field.add("default", EditorJson.json(setting.defaultValue()));
                field.addProperty("level", setting.level());
                field.addProperty("secret", setting.secret());
                fields.add(field);
            }
            settings.add("fields", fields);
            final JsonArray scopes = new JsonArray();
            for (final SettingScope scope : this.settings.scopes()) {
                final JsonObject item = new JsonObject();
                item.addProperty("scope", scope.scope());
                item.addProperty("version", scope.version());
                item.add("overrides", EditorJson.json(scope.overrides()));
                scopes.add(item);
            }
            settings.add("scopes", scopes);
            final JsonArray servers = new JsonArray();
            for (final Server server : this.settings.servers()) {
                final JsonObject item = new JsonObject();
                item.addProperty("id", server.id());
                item.addProperty("name", server.name());
                item.add("categories", EditorJson.json(server.categories()));
                item.addProperty("platform", server.platform());
                item.addProperty("pluginVersion", server.pluginVersion());
                item.addProperty("lastSeenAt", server.lastSeenAt());
                item.add("fileValues", EditorJson.json(server.fileValues()));
                servers.add(item);
            }
            settings.add("servers", servers);
            root.add("settings", settings);
        }
        if (this.messages != null) {
            final JsonObject messages = new JsonObject();
            final JsonArray fields = new JsonArray();
            for (final Message message : this.messages.fields()) {
                final JsonObject field = new JsonObject();
                field.addProperty("key", message.key());
                field.addProperty("description", message.description());
                field.add("placeholders", EditorJson.json(message.placeholders()));
                field.addProperty("default", message.defaultText());
                fields.add(field);
            }
            messages.add("fields", fields);
            final JsonArray locales = new JsonArray();
            for (final LocaleTexts texts : this.messages.locales()) {
                final JsonObject locale = new JsonObject();
                locale.addProperty("locale", texts.locale());
                locale.add("fileTexts", EditorJson.json(texts.fileTexts()));
                locales.add(locale);
            }
            messages.add("locales", locales);
            final JsonArray scopes = new JsonArray();
            for (final MessageScope scope : this.messages.scopes()) {
                final JsonObject item = new JsonObject();
                item.addProperty("scope", scope.scope());
                item.addProperty("locale", scope.locale());
                item.addProperty("version", scope.version());
                item.add("overrides", EditorJson.json(scope.overrides()));
                scopes.add(item);
            }
            messages.add("scopes", scopes);
            root.add("messages", messages);
        }
        if (this.templates != null) {
            final JsonArray items = new JsonArray();
            for (final Template template : this.templates.items()) {
                final JsonObject item = new JsonObject();
                item.addProperty("scope", template.scope());
                item.addProperty("name", template.name());
                item.addProperty("version", template.version());
                item.add("definition", EditorJson.json(template.definition()));
                items.add(item);
            }
            final JsonObject templates = new JsonObject();
            templates.add("items", items);
            root.add("templates", templates);
        }
        if (!this.recentChanges.isEmpty()) {
            final JsonArray recent = new JsonArray();
            for (final RecentChange change : this.recentChanges) {
                final JsonObject item = new JsonObject();
                item.addProperty("at", change.at());
                item.addProperty("actor", change.actor());
                item.addProperty("action", change.action());
                item.addProperty("kind", change.kind());
                item.addProperty("scope", change.scope());
                item.add("locale", change.locale() == null ? JsonNull.INSTANCE
                        : new JsonPrimitive(change.locale()));
                item.addProperty("target", change.target());
                item.add("keys", EditorJson.json(change.keys()));
                recent.add(item);
            }
            root.add("recentChanges", recent);
        }
        final JsonObject lookups = new JsonObject();
        lookups.add("servers", EditorJson.json(this.lookups.servers()));
        lookups.add("categories", EditorJson.json(this.lookups.categories()));
        lookups.add("locales", EditorJson.json(this.lookups.locales()));
        root.add("lookups", lookups);
        return root;
    }
}
