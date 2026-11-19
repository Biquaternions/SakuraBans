package net.serlith.sakurabans.api.settings;

import java.util.List;
import java.util.Optional;

/**
 * One setting in the schema, enough for a UI to render a form field. Implemented by SakuraBans;
 * don't implement it.
 */
public interface SettingField {

    /**
     * The setting's key, as a dot-separated path of {@code config.yml} keys.
     *
     * @return the key, for example {@code alts.connection-limiter.window}
     */
    String key();

    /**
     * The value's type.
     *
     * @return the type
     */
    SettingType type();

    /**
     * The built-in default value.
     *
     * @return the default, in the form {@link #type()} describes
     */
    Object defaultValue();

    /**
     * What the setting does, in English.
     *
     * @return the description
     */
    String description();

    /**
     * The allowed values of an {@link SettingType#ENUM} setting.
     *
     * @return the options, empty for other types
     */
    List<String> options();

    /**
     * The smallest allowed number (or the shortest duration, in seconds).
     *
     * @return the minimum, or empty if unbounded
     */
    Optional<Double> min();

    /**
     * The largest allowed number (or the longest duration, in seconds).
     *
     * @return the maximum, or empty if unbounded
     */
    Optional<Double> max();

    /**
     * Whether the setting applies to the whole network. Local settings (storage, server
     * identity) are shown but can only be changed in the instance's files.
     *
     * @return {@code true} if network-wide
     */
    boolean networkWide();

    /**
     * Whether it can be changed through {@link SettingsService#update}.
     *
     * @return {@code true} for network-wide settings that are safe to change at runtime
     */
    boolean editable();

    /**
     * Whether a change only takes effect after a restart.
     *
     * @return {@code true} if a restart is needed
     */
    boolean restartRequired();
}
