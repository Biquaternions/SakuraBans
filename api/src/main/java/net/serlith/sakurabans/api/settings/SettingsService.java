package net.serlith.sakurabans.api.settings;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.model.Actor;

/**
 * The settings. The effective value of a network-wide setting is its database override if one is
 * set, otherwise the value in {@code config.yml}; networks that don't use the API keep editing the
 * file. Reads answer from memory on any thread; changes are validated like the file, stored,
 * audited and synced. Implemented by SakuraBans; don't implement it.
 */
public interface SettingsService {

    /**
     * Every setting SakuraBans knows.
     *
     * @return the schema, in file order
     */
    List<SettingField> schema();

    /**
     * The effective settings.
     *
     * @return the current snapshot
     */
    SettingsSnapshot settings();

    /**
     * Overrides network-wide settings.
     *
     * @param patch the new values by key; only editable keys
     * @param expectedVersion the snapshot version the caller last saw
     * @param actor who changes them
     * @return the new snapshot; {@code ValidationFailed} with the key as path for unknown,
     *         read-only or invalid values
     */
    CompletableFuture<AdminResult<SettingsSnapshot>> update(
            Map<String, Object> patch, long expectedVersion, Actor actor);

    /**
     * Removes overrides, so the file's values apply again.
     *
     * @param keys the keys to reset
     * @param expectedVersion the snapshot version the caller last saw
     * @param actor who resets them
     * @return the new snapshot
     */
    CompletableFuture<AdminResult<SettingsSnapshot>> reset(
            Set<String> keys, long expectedVersion, Actor actor);
}
