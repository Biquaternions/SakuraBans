package net.serlith.sakurabans.core.geoip;

import java.net.InetAddress;
import java.util.Optional;

public interface CountryLookup {

    enum State {
        DISABLED,
        NO_DATABASE,
        READY
    }

    Optional<Country> country(InetAddress address);

    State state();

    default Optional<String> countryName(final InetAddress address) {
        return this.state() == State.DISABLED ? Optional.empty()
                : this.country(address).map(Country::name);
    }

    static CountryLookup disabled() {
        return DisabledCountryLookup.INSTANCE;
    }
}
