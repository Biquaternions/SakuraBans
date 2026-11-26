package net.serlith.sakurabans.core.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;

public final class FakeCommandActor implements CommandActor {

    private final String name;
    private final @Nullable UUID uuid;
    private final Set<String> permissions = ConcurrentHashMap.newKeySet();
    private final List<Component> messages = new CopyOnWriteArrayList<>();
    private final Audience audience = new Audience() {
        @Override
        public void sendMessage(final Component message) {
            FakeCommandActor.this.messages.add(message);
        }
    };

    private FakeCommandActor(final String name, final @Nullable UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public static FakeCommandActor theConsole() {
        return new FakeCommandActor("CONSOLE", null);
    }

    public static FakeCommandActor player(final UUID uuid, final String name,
            final String... permissions) {
        final FakeCommandActor actor = new FakeCommandActor(name, uuid);
        actor.permissions.addAll(List.of(permissions));
        return actor;
    }

    public Set<String> permissions() {
        return Set.copyOf(this.permissions);
    }

    public FakeCommandActor grant(final String... permissions) {
        this.permissions.addAll(List.of(permissions));
        return this;
    }

    public List<String> texts() {
        return this.messages.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
    }

    public List<Component> components() {
        return List.copyOf(this.messages);
    }

    public String last() {
        final List<String> texts = this.texts();
        return texts.isEmpty() ? "" : texts.getLast();
    }

    public void clear() {
        this.messages.clear();
    }

    @Override
    public Audience audience() {
        return this.audience;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public Optional<UUID> uuid() {
        return Optional.ofNullable(this.uuid);
    }

    @Override
    public boolean console() {
        return this.uuid == null;
    }

    @Override
    public boolean hasPermission(final String node) {
        return this.console() || this.permissions.contains(node);
    }

    @Override
    public Optional<Locale> locale() {
        return Optional.empty();
    }

    @Override
    public Optional<String> currentServer() {
        return Optional.empty();
    }
}
