package net.serlith.sakurabans.core.storage.repository;

import org.jspecify.annotations.Nullable;

public sealed interface EntityWrite<T> {

    record Done<T>(T value, @Nullable T previous) implements EntityWrite<T> {
    }

    record Unchanged<T>(T value) implements EntityWrite<T> {
    }

    record Taken<T>(T existing) implements EntityWrite<T> {
    }

    record NotFound<T>() implements EntityWrite<T> {
    }

    record Conflict<T>(long currentVersion) implements EntityWrite<T> {
    }

    record Refused<T>(String path, String message) implements EntityWrite<T> {
    }

}
