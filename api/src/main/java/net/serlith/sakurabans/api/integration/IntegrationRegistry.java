package net.serlith.sakurabans.api.integration;

/**
 * Where other plugins register their integrations. Several of each kind may be registered; they
 * are consulted in registration order. Implemented by SakuraBans; don't implement it.
 */
public interface IntegrationRegistry {

    /**
     * Registers a guard, consulted before every action.
     *
     * @param guard the guard
     * @return the registration
     */
    Registration register(ActionGuard guard);

    /**
     * Registers an alt source, whose results are merged into alt lookups and join notices.
     *
     * @param source the source
     * @return the registration
     */
    Registration register(AltSource source);

    /**
     * Registers an evidence capturer for its {@link EvidenceCapturer#type()}. A later capturer
     * for the same type replaces the earlier one while it is registered.
     *
     * @param capturer the capturer
     * @return the registration
     */
    Registration register(EvidenceCapturer capturer);

    /**
     * Registers a profile source, asked when SakuraBans doesn't know a name or UUID yet.
     *
     * @param source the source
     * @return the registration
     */
    Registration register(ProfileSource source);
}
