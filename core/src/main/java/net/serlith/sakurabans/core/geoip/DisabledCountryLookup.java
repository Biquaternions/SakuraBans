package net.serlith.sakurabans.core.geoip;

import java.net.InetAddress;
import java.util.Optional;

final class DisabledCountryLookup implements CountryLookup {

    static final CountryLookup INSTANCE = new DisabledCountryLookup();

    private DisabledCountryLookup() {
    }

    @Override
    public Optional<Country> country(final InetAddress address) {
        return Optional.empty();
    }

    @Override
    public State state() {
        return State.DISABLED;
    }
}
