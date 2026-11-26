package net.serlith.sakurabans.core.importer;

import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import org.jspecify.annotations.Nullable;

/** Where an {@link ImportSource} puts what it reads. Not thread-safe: call it from one thread. */
public interface SourceReader {

    /** A name and/or address of a player, merged into the known players. */
    void player(ImportedPlayer player);

    /** A punishment, with its external id ({@link SourceContext#externalId}). */
    void punishment(ImportedPunishment punishment);

    /**
     * A row that can't be imported (no victim, an unknown type, ...); it is counted and listed
     * in the report, and the import goes on.
     *
     * @param table the source table (or file) it came from
     * @param id its id there
     * @param problem what is wrong, in English
     */
    void failure(String table, String id, String problem);

    /**
     * The text cut to {@code maxLength} characters, counting a truncation in the report when it
     * was longer. Use it for every text with an API limit ({@link Lengths}).
     */
    String fit(String text, int maxLength);

    /** A reason as the API takes it: colour codes stripped, fit to {@link Lengths#MAX_REASON}. */
    default String reason(final @Nullable String raw) {
        return raw == null ? "" : this.fit(ImportValues.plain(raw).strip(), Lengths.MAX_REASON);
    }
}
