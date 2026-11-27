package net.serlith.sakurabans.core.geoip;

import com.google.common.base.Preconditions;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public record Country(String code, String name) {

    public Country {
        Preconditions.checkArgument(code.length() == 2, "not an alpha-2 code: %s", code);
        Preconditions.checkArgument(!name.isBlank(), "a country needs a name");
    }

    public static Country of(final String code, final @Nullable String englishName) {
        final String upper = code.toUpperCase(Locale.ROOT);
        if (englishName != null && !englishName.isBlank()) {
            return new Country(upper, englishName.strip());
        }
        final String known = Locale.of("", upper).getDisplayCountry(Locale.ENGLISH);
        return new Country(upper, known.isBlank() ? upper : known);
    }

    static @Nullable Country fromRecord(final @Nullable String countryCode,
            final @Nullable String countryName, final @Nullable String registeredCode,
            final @Nullable String registeredName) {
        if (valid(countryCode)) {
            return of(countryCode, countryName);
        }
        if (valid(registeredCode)) {
            return of(registeredCode, registeredName);
        }
        return null;
    }

    private static boolean valid(final @Nullable String code) {
        return code != null && code.length() == 2 && Character.isLetter(code.charAt(0))
                && Character.isLetter(code.charAt(1));
    }
}
