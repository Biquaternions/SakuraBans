package net.serlith.sakurabans.api.settings;

/**
 * The type of a setting's value, and how it appears in a {@link SettingsSnapshot}.
 */
public enum SettingType {

    /** A {@code Boolean}. */
    BOOLEAN,

    /** A whole number, as a {@code Long}. */
    INTEGER,

    /** A decimal number, as a {@code Double}. */
    DECIMAL,

    /** A {@code String}. */
    STRING,

    /** A duration, as an ISO-8601 {@code String} such as {@code PT10M}. */
    DURATION,

    /** One of {@link SettingField#options()}, as a {@code String}. */
    ENUM,

    /** A {@code List<String>}. */
    STRING_LIST,

    /** A {@code Map<String, Object>} with values of these same types. */
    MAP
}
