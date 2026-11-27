package net.serlith.sakurabans.core.geoip;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CountryTest {

    @Test
    void theRegisteredCountryStandsInWhenTheRecordHasNoCountry() {
        Assertions.assertThat(Country.fromRecord(null, null, "DE", "Germany"))
                .isEqualTo(new Country("DE", "Germany"));
        Assertions.assertThat(Country.fromRecord("SE", "Sweden", "DE", "Germany"))
                .isEqualTo(new Country("SE", "Sweden"));
        Assertions.assertThat(Country.fromRecord(null, null, null, null)).isNull();
    }

    @Test
    void withoutTheDatabaseNameTheJdkNamesTheCountry() {
        Assertions.assertThat(Country.of("de", null)).isEqualTo(new Country("DE", "Germany"));
        Assertions.assertThat(Country.of("FR", " ")).isEqualTo(new Country("FR", "France"));
    }
}
