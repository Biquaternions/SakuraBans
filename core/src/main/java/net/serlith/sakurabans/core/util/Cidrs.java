package net.serlith.sakurabans.core.util;

import java.net.InetAddress;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.model.AddressRangeVictim;

public final class Cidrs {

    private static final Pattern CIDR = Pattern.compile("(.+)/(\\d{1,3})");
    private static final Pattern WILDCARD =
            Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){0,3})((?:\\.\\*){1,4})|\\*(?:\\.\\*){3}");

    private Cidrs() {
    }

    /**
     * Parses {@code 10.0.0.0/8}, {@code 2001:db8::/32}, or an IPv4 wildcard such as
     * {@code 10.0.*.*} (each {@code *} replaces 8 bits; {@code *.*.*.*} is rejected, because a
     * range must have a prefix). Host bits are cleared.
     */
    public static Optional<AddressRangeVictim> parse(final String text) {
        final String input = text.strip();
        final Matcher cidr = CIDR.matcher(input);
        if (cidr.matches()) {
            final Optional<InetAddress> network = InetAddresses.parseLiteral(cidr.group(1));
            final int prefix = Integer.parseInt(cidr.group(2));
            final int bits = network.map(address -> address.getAddress().length * 8).orElse(0);
            if (network.isEmpty() || prefix < 1 || prefix > bits) {
                return Optional.empty();
            }
            return Optional.of(AddressRangeVictim.of(network.get(), prefix));
        }
        final Matcher wildcard = WILDCARD.matcher(input);
        if (!wildcard.matches() || wildcard.group(1) == null) {
            return Optional.empty();
        }
        final String[] fixed = wildcard.group(1).split("\\.");
        final int stars = wildcard.group(2).length() / 2;
        if (fixed.length + stars != 4) {
            return Optional.empty();
        }
        final StringBuilder network = new StringBuilder(wildcard.group(1));
        for (int i = 0; i < stars; i++) {
            network.append(".0");
        }
        return InetAddresses.parseLiteral(network.toString())
                .map(address -> AddressRangeVictim.of(address, fixed.length * 8));
    }
}
