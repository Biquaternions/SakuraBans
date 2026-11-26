package net.serlith.sakurabans.core.importer;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.serlith.sakurabans.core.configuration.settings.ImportSettings;

public final class ImportSources {

    public static final String SELF = ImportSettings.SELF;

    private final Map<String, ImportSource> sources;

    public ImportSources(final List<ImportSource> sources) {
        final Map<String, ImportSource> byId = new LinkedHashMap<>();
        for (final ImportSource source : sources) {
            Preconditions.checkArgument(!source.id().equals(SELF) && byId.put(source.id(),
                    source) == null, "two import sources with the id %s", source.id());
        }
        this.sources = byId;
    }

    public static ImportSources standard() {
        final List<ImportSource> sources = new ArrayList<>();
        sources.add(new LibertyBansSource());
        sources.add(new LiteBansSource());
        sources.add(new VanillaSource());
        sources.add(new AdvancedBanSource());
        sources.add(new BanManagerSource());
        return new ImportSources(sources);
    }

    public List<String> ids() {
        final List<String> ids = new ArrayList<>();
        ids.add(SELF);
        ids.addAll(this.sources.keySet());
        return List.copyOf(ids);
    }

    public boolean known(final String id) {
        return SELF.equals(id) || this.sources.containsKey(id);
    }

    public Optional<ImportSource> source(final String id) {
        return Optional.ofNullable(this.sources.get(id));
    }

}
