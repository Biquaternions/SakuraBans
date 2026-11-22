package net.serlith.sakurabans.core.command.parser;

import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PunishTailTest {

    @Test
    void readsDurationTemplateAndReason() {
        Assertions.assertThat(PunishTail.parse("1d12h #Spam  too  many messages", true))
                .isEqualTo(new PunishTail(Duration.ofHours(36), false, "spam",
                        "too  many messages"));
        Assertions.assertThat(PunishTail.parse("perm hacking", true))
                .isEqualTo(new PunishTail(null, true, null, "hacking"));
        Assertions.assertThat(PunishTail.parse("#spam", true))
                .isEqualTo(new PunishTail(null, false, "spam", null));
    }

    @Test
    void firstWordsAreReasonsWhenTheyAreNoDuration() {
        Assertions.assertThat(PunishTail.parse("griefing at spawn", true))
                .isEqualTo(new PunishTail(null, false, null, "griefing at spawn"));
        Assertions.assertThat(PunishTail.parse("7d griefing", false))
                .isEqualTo(new PunishTail(null, false, null, "7d griefing"));
        Assertions.assertThat(PunishTail.parse("# alone", true).reason()).isEqualTo("# alone");
    }

    @Test
    void emptyTails() {
        Assertions.assertThat(PunishTail.parse(null, true)).isEqualTo(PunishTail.EMPTY);
        Assertions.assertThat(PunishTail.parse("   ", true)).isEqualTo(PunishTail.EMPTY);
        Assertions.assertThat(PunishTail.parse("30m", true))
                .isEqualTo(new PunishTail(Duration.ofMinutes(30), false, null, null));
    }
}
