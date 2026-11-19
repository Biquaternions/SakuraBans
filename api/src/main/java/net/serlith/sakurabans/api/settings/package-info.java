/**
 * Network-wide settings: a schema, the effective values, and overrides stored in the database on
 * top of {@code config.yml}.
 *
 * <p>Values are JSON-compatible: {@code Boolean}, {@code Long}, {@code Double}, {@code String}
 * (durations as ISO-8601, for example {@code PT10M}), {@code List<String>} and
 * {@code Map<String, Object>} of the same types.
 */
@NullMarked
package net.serlith.sakurabans.api.settings;

import org.jspecify.annotations.NullMarked;
