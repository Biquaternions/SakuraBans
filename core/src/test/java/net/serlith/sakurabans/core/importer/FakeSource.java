package net.serlith.sakurabans.core.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** A source without a database that hands over what the test gives it. */
public final class FakeSource implements ImportSource {

    public static final String ID = "fake";

    private final List<Consumer<SourceReader>> rows = new ArrayList<>();

    /** Adds a row: whatever the consumer puts into the reader. */
    public FakeSource row(final Consumer<SourceReader> row) {
        this.rows.add(row);
        return this;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<String> databaseSection() {
        return Optional.empty();
    }

    @Override
    public Map<String, Long> survey(final SourceContext context) {
        return Map.of("rows", (long) this.rows.size());
    }

    @Override
    public void read(final SourceContext context, final SourceReader reader) {
        this.rows.forEach(row -> row.accept(reader));
    }
}
