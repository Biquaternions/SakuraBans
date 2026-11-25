package net.serlith.sakurabans.core.storage;

import java.util.HashSet;
import java.util.Set;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class DisplayIdCodecTest extends DatabaseTest {

    private DisplayIdCodec codec(final String style) {
        final DisplayIdCodec codec = new DisplayIdCodec(new MetaRepository(this.storage),
                TestConfig.load(this.dir, "display:\n  id-style: " + style + "\n",
                        new RecordingLogger()));
        codec.load().join();
        return codec;
    }

    @Test
    void codesRoundTripAndNeverLookLikeNumbers() {
        final DisplayIdCodec codec = this.codec("SCRAMBLED");
        final Set<String> seen = new HashSet<>();
        for (long id = 1; id <= 5_000; id++) {
            final String code = codec.encode(id);
            Assertions.assertThat(code).hasSize(8).matches("[A-H][A-HJ-NP-Z2-9]{7}");
            Assertions.assertThat(seen.add(code)).isTrue();
            Assertions.assertThat(codec.decode(code)).hasValue(id);
            Assertions.assertThat(codec.decode("#" + code.toLowerCase())).hasValue(id);
        }
        final long last = DisplayIdCodec.LIMIT - 1;
        Assertions.assertThat(codec.decode(codec.encode(last))).hasValue(last);
        Assertions.assertThat(codec.display(DisplayIdCodec.LIMIT))
                .isEqualTo(Long.toString(DisplayIdCodec.LIMIT));
    }

    @Test
    void theStyleDecidesWhatIsShownButEveryFormIsRead() {
        final DisplayIdCodec numeric = this.codec("NUMERIC");
        Assertions.assertThat(numeric.display(12)).isEqualTo("12");
        final String code = numeric.encode(12);
        Assertions.assertThat(numeric.decode(code)).hasValue(12);
        Assertions.assertThat(numeric.decode("12/" + code)).hasValue(12);
        Assertions.assertThat(numeric.decode("0")).isEmpty();
        Assertions.assertThat(numeric.decode("ZZZZZZZZ")).isEmpty();
        Assertions.assertThat(numeric.decode("hello")).isEmpty();

        // The secret is shared: a second instance on the database shows the same code.
        Assertions.assertThat(this.codec("SCRAMBLED").display(12)).isEqualTo(code);
        Assertions.assertThat(this.codec("BOTH").display(12)).isEqualTo("12/" + code);
    }
}
