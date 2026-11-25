package net.serlith.sakurabans.core.integration;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.serlith.sakurabans.api.integration.ActionGuard;
import net.serlith.sakurabans.api.integration.IntegrationRegistry;
import net.serlith.sakurabans.api.integration.ProfileSource;
import net.serlith.sakurabans.api.integration.Registration;

public final class SakuraIntegrationRegistry implements IntegrationRegistry {

    private final List<ActionGuard> guards = new CopyOnWriteArrayList<>();
    private final List<ProfileSource> profileSources = new CopyOnWriteArrayList<>();

    @Override
    public Registration register(final ActionGuard guard) {
        return add(this.guards, Preconditions.checkNotNull(guard, "guard"));
    }

    @Override
    public Registration register(final ProfileSource source) {
        return add(this.profileSources, Preconditions.checkNotNull(source, "source"));
    }

    public List<ActionGuard> guards() {
        return List.copyOf(this.guards);
    }

    public List<ProfileSource> profileSources() {
        return List.copyOf(this.profileSources);
    }

    private static <T> Registration add(final List<T> list, final T value) {
        Preconditions.checkArgument(list.stream().noneMatch(existing -> existing == value),
                "%s is registered already", value);
        list.add(value);
        final AtomicBoolean active = new AtomicBoolean(true);
        return new Registration() {
            @Override
            public boolean active() {
                return active.get();
            }

            @Override
            public void unregister() {
                if (active.compareAndSet(true, false)) {
                    list.removeIf(existing -> existing == value);
                }
            }
        };
    }

}
