package net.serlith.sakurabans.core.importer;

import com.google.common.base.Preconditions;
import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;
import org.jspecify.annotations.Nullable;

public record SourceContext(String sourceId, ImportSettings settings,
        @Nullable SourceDatabase databaseOrNull) {

    public SourceContext {
        Preconditions.checkNotNull(sourceId, "sourceId");
        Preconditions.checkNotNull(settings, "settings");
    }

    public SourceDatabase database() {
        Preconditions.checkState(this.databaseOrNull != null,
                "the source %s declared no database section", this.sourceId);
        return this.databaseOrNull;
    }

    /**
     * The external id of a row: {@code <source>:<table>:<id>}, for example
     * {@code litebans:bans:42}. {@code table} is the name without prefix.
     */
    public String externalId(final String table, final Object id) {
        final String externalId = this.sourceId + ":" + table + ":" + id;
        Preconditions.checkArgument(externalId.length() <= ImportedPunishment.MAX_EXTERNAL_ID,
                "external id longer than %s characters: %s", ImportedPunishment.MAX_EXTERNAL_ID,
                externalId);
        return externalId;
    }
}
