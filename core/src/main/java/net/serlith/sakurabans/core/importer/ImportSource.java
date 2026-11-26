package net.serlith.sakurabans.core.importer;

import java.util.Map;
import java.util.Optional;

/**
 * A system punishments can be imported from ({@code /sb import <id>}).
 *
 * <p>To add one: implement this, add one line to {@link ImportSources#standard}, and read the
 * section of {@code import.yml} named by {@link #databaseSection()} (a JDBC source) or the
 * {@code vanilla} paths of {@link SourceContext#settings()} (a file source).
 *
 * <p>{@link #read} streams rows into the {@link SourceReader}: each call is buffered and stored
 * in batches, so a source never holds more than one row in memory. Use
 * {@link SourceDatabase#stream} for queries. A row that can't be converted is reported with
 * {@link SourceReader#failure} and the import goes on; only an exception (the source can't be
 * read at all) stops it. Give every punishment an external id from
 * {@link SourceContext#externalId}: a second run skips what the first imported. Emit a
 * player's names before its address-only rows: a player without any name can't be stored.
 * {@link ImportValues} converts the usual columns (UUIDs, addresses, epoch times, colour
 * codes, scopes, the console operator).
 *
 * <p>Sources are called on a worker thread, one import at a time, and may block.
 */
public interface ImportSource {

    /** The id the command takes and that external ids start with: lower case, no spaces. */
    String id();

    /**
     * The {@code import.yml} section with the {@code jdbc-url}, {@code username},
     * {@code password} and {@code table-prefix} to open; empty for a source that reads files.
     * When present, {@link SourceContext#database()} is open while the source runs.
     */
    Optional<String> databaseSection();

    /**
     * What an import would read, shown before it runs: rows by table (or file). It should be
     * cheap: counts, not conversions. Throw if the source isn't there (a missing table, file).
     */
    Map<String, Long> survey(SourceContext context) throws Exception;

    /** Reads everything, in order, into the reader. */
    void read(SourceContext context, SourceReader reader) throws Exception;
}
