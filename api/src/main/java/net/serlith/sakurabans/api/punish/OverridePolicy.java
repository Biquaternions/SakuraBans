package net.serlith.sakurabans.api.punish;

/**
 * What happens when the victim already has an active punishment of the same type in the same
 * scope. Warnings stack and ignore this policy.
 */
public enum OverridePolicy {

    /** Keep the existing punishment and fail with {@code ALREADY_PUNISHED}. */
    REJECT,

    /** End the existing punishment ({@code SUPERSEDED}) and create the new one. */
    REPLACE,

    /**
     * Replace the existing punishment only if the new one lasts longer (a permanent one is the
     * longest); otherwise fail with {@code ALREADY_PUNISHED}.
     */
    REPLACE_IF_LONGER
}
