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
     * Registers a profile source, asked when SakuraBans doesn't know a name or UUID yet.
     *
     * @param source the source
     * @return the registration
     */
    Registration register(ProfileSource source);
}
