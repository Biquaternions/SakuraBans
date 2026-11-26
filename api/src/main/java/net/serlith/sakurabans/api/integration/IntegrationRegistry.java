package net.serlith.sakurabans.api.integration;

/**
 * Where other plugins register their integrations. Several may be registered; they are consulted
 * in registration order. Implemented by SakuraBans; don't implement it.
 */
public interface IntegrationRegistry {

    /**
     * Registers a profile source, asked when SakuraBans doesn't know a name or UUID yet.
     *
     * @param source the source
     * @return the registration
     */
    Registration register(ProfileSource source);
}
