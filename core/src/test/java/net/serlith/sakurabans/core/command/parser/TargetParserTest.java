package net.serlith.sakurabans.core.command.parser;

import java.util.UUID;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.util.Cidrs;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TargetParserTest {

    @Test
    void recognisesEveryKindOfTarget() {
        final UUID uuid = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

        Assertions.assertThat(TargetParser.parse("#42")).contains(new TargetInput.IdInput(42));
        Assertions.assertThat(TargetParser.parse("8667ba71b85a4004af54457a9734eed7"))
                .contains(new TargetInput.UuidInput(uuid));
        Assertions.assertThat(TargetParser.parse("203.0.113.7")).contains(
                new TargetInput.AddressInput(InetAddresses.parseLiteral("203.0.113.7")
                        .orElseThrow()));
        Assertions.assertThat(TargetParser.parse("10.0.*.*")).contains(
                new TargetInput.RangeInput(Cidrs.parse("10.0.0.0/16").orElseThrow()));
        Assertions.assertThat(TargetParser.parse("Steve_99"))
                .contains(new TargetInput.NameInput("Steve_99"));
    }

    @Test
    void rejectsWhatIsNoTarget() {
        Assertions.assertThat(TargetParser.parse("#0")).isEmpty();
        Assertions.assertThat(TargetParser.parse("#abc")).isEmpty();
        Assertions.assertThat(TargetParser.parse("10.*.1.*")).isEmpty();
        Assertions.assertThat(TargetParser.parse("not-a-name")).isEmpty();
        Assertions.assertThat(TargetParser.parse("x".repeat(17))).isEmpty();
    }
}
