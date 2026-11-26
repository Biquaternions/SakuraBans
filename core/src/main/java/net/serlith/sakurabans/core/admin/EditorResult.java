package net.serlith.sakurabans.core.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import net.serlith.sakurabans.api.validation.FieldError;
import org.jspecify.annotations.Nullable;

/** What the site shows after a change set was applied or refused (protocol section 9). */
public record EditorResult(long sequence, boolean applied, boolean live, List<Entry> changes,
        List<FieldError> errors, int appliedCount, int unchangedCount, int conflictCount,
        @Nullable Long payloadRevision) {

    public EditorResult {
        changes = List.copyOf(changes);
        errors = List.copyOf(errors);
    }

    /**
     * {@code scope} is the setting's or message's ({@code ""} for templates); {@code detail} is
     * plain text, never MiniMessage, and never a secret's value.
     */
    public record Entry(String section, String scope, String target, String action,
            String detail) {
    }

    public JsonObject json() {
        final JsonObject root = new JsonObject();
        root.addProperty("format", EditorPayload.FORMAT);
        root.addProperty("sequence", this.sequence);
        root.addProperty("outcome", this.applied ? "applied" : "refused");
        root.addProperty("by", this.live ? "live" : "command");
        final JsonArray changes = new JsonArray();
        for (final Entry entry : this.changes) {
            final JsonObject change = new JsonObject();
            change.addProperty("section", entry.section());
            change.addProperty("scope", entry.scope());
            change.addProperty("target", entry.target());
            change.addProperty("action", entry.action());
            change.addProperty("detail", entry.detail());
            changes.add(change);
        }
        root.add("changes", changes);
        final JsonArray errors = new JsonArray();
        for (final FieldError error : this.errors) {
            final JsonObject item = new JsonObject();
            item.addProperty("path", error.path());
            item.addProperty("message", error.message());
            errors.add(item);
        }
        root.add("errors", errors);
        final JsonObject summary = new JsonObject();
        summary.addProperty("applied", this.appliedCount);
        summary.addProperty("unchanged", this.unchangedCount);
        summary.addProperty("conflicts", this.conflictCount);
        root.add("summary", summary);
        root.add("payloadRevision", this.payloadRevision == null ? JsonNull.INSTANCE
                : new JsonPrimitive(this.payloadRevision));
        return root;
    }
}
