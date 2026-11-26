package net.serlith.sakurabans.core.player;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.serlith.sakurabans.api.integration.IntegrationRegistry;
import net.serlith.sakurabans.api.integration.ProfileSource;
import net.serlith.sakurabans.api.integration.Registration;

public final class SakuraIntegrationRegistry implements IntegrationRegistry {

    private final List<ProfileSource> profileSources = new CopyOnWriteArrayList<>();

    @Override
    public Registration register(final ProfileSource source) {
        Preconditions.checkNotNull(source, "source");
        Preconditions.checkArgument(this.profileSources.stream()
                .noneMatch(existing -> existing == source), "%s is registered already", source);
        this.profileSources.add(source);
        final AtomicBoolean active = new AtomicBoolean(true);
        return new Registration() {
            @Override
            public boolean active() {
                return active.get();
            }

            @Override
            public void unregister() {
                if (active.compareAndSet(true, false)) {
                    SakuraIntegrationRegistry.this.profileSources
                            .removeIf(existing -> existing == source);
                }
            }
        };
    }

    public List<ProfileSource> profileSources() {
        return List.copyOf(this.profileSources);
    }

}
