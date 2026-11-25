package net.serlith.sakurabans.core.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.model.SakuraPunishment;
import net.serlith.sakurabans.core.model.SakuraPunishmentEnd;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-18T12:00:00Z");
    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @TempDir
    private Path dir;

    private final RecordingLogger log = new RecordingLogger();
    private MessageService messages;
    private Formats formats;
    private Placeholders placeholders;

    @BeforeEach
    void prepare() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"), "display:\n  timezone: UTC\n");
        final ConfigService config = new ConfigService(this.dir, this.log.logger());
        config.load();
        this.messages = new MessageService(this.dir, config, this.log.logger());
        this.messages.load();
        this.formats = new Formats(this.messages, config);
        this.placeholders = new Placeholders(this.messages, this.formats);
    }

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static SakuraPunishment punishment(final Victim victim, final String reason,
            final @Nullable Instant expiresAt) {
        return new SakuraPunishment(42, "K7Q2", PunishmentType.BAN, "ban", false,
                null, victim, PlayerOperator.of(UUID.randomUUID(), "Moderator"),
                "in-game", ServerScope.of("lobby"), reason, CREATED, expiresAt,
                "lobby", Visibility.NORMAL, null, null,
                null, List.of(), 1);
    }

    @Test
    void thePrefixIsAvailableEverywhere() {
        assertThat(plain(this.messages.render(MessageKey.ERROR_NOT_READY, Locale.US)))
                .startsWith("SakuraBans » ").contains("still starting");
    }

    @Test
    void userTextNeverInjectsTags() {
        final Punishment ban = punishment(PlayerVictim.of(STEVE),
                "<red>hacks</red> <click:run_command:/op>", null);
        final Component rendered = MiniMessage.miniMessage()
                .deserialize("<reason>", this.placeholders.punishment(ban, Locale.US, CREATED,
                        uuid -> Optional.empty(), false));

        assertThat(plain(rendered)).isEqualTo("<red>hacks</red> <click:run_command:/op>");
        assertThat(rendered.color()).isNotEqualTo(NamedTextColor.RED);
        assertThat(rendered.children()).allSatisfy(child ->
                assertThat(child.clickEvent()).isNull());
    }

    @Test
    void everyPunishmentTagResolves() {
        final Punishment ban = new SakuraPunishment(42, "K7Q2", PunishmentType.BAN, "ban", false,
                null, PlayerVictim.of(STEVE), ConsoleOperator.INSTANCE, "console",
                ServerScope.of("lobby"), "Spam", CREATED, CREATED.plus(
                        Duration.ofDays(1).plusHours(12)), "lobby",
                Visibility.NORMAL, null, new SakuraPunishmentEnd(
                        EndKind.REVOKED, CREATED.plusSeconds(60),
                        Actor.of(PlayerOperator.of(STEVE, "Admin"), "web"),
                        "appeal"), null, List.of(), 2);
        final String template = "<victim>|<operator>|<reason>|<duration>|<remaining>|<expires>|"
                + "<id>|<display_id>|<scope>|<type>|<kind>|<server>|<revoker>|<revoke_reason>";

        final String text = plain(MiniMessage.miniMessage()
                .deserialize(template, this.placeholders.punishment(ban, Locale.US,
                        CREATED.plus(Duration.ofHours(12)), uuid -> Optional.of("Steve"), false)));

        assertThat(text).isEqualTo("Steve|Console|Spam|1 day and 12 hours|1 day|"
                + "20 Nov 2026 00:00|42|K7Q2|server lobby|ban|ban|lobby|Admin|appeal");
    }

    @Test
    void addressesAreCensoredUnlessAllowed() throws Exception {
        final Victim address = AddressVictim.of(InetAddress.getByName("203.0.113.7"));

        assertThat(this.placeholders.victim(address, uuid -> Optional.empty(), false))
                .isEqualTo("203.0.*.*");
        assertThat(this.placeholders.victim(address, uuid -> Optional.empty(), true))
                .isEqualTo("203.0.113.7");
        assertThat(this.placeholders.victim(PlayerVictim.of(STEVE), uuid -> Optional.empty(),
                true)).isEqualTo(STEVE.toString());
    }

    @Test
    void remainingTimeShowsTheTwoLargestUnits() {
        final Duration days = Duration.ofDays(3).plusHours(4).plusMinutes(5).plusSeconds(6);
        assertThat(this.formats.approximate(days, Locale.US)).isEqualTo("3 days and 4 hours");
        assertThat(this.formats.approximate(Duration.ofHours(2).plusMinutes(5).plusSeconds(9),
                Locale.US)).isEqualTo("2 hours and 5 minutes");
        assertThat(this.formats.approximate(Duration.ofMinutes(7).plusSeconds(30), Locale.US))
                .isEqualTo("7 minutes and 30 seconds");
        assertThat(this.formats.approximate(Duration.ofSeconds(42), Locale.US))
                .isEqualTo("42 seconds");
        // A unit below the largest that is zero is left out, not replaced by a smaller one.
        assertThat(this.formats.approximate(Duration.ofDays(2).plusMinutes(30), Locale.US))
                .isEqualTo("2 days");
        assertThat(this.formats.approximate(Duration.ofDays(400), Locale.US))
                .isEqualTo("1 year and 35 days");
    }

    @Test
    void durationsReadNaturally() {
        assertThat(this.formats.duration(Duration.ofSeconds(1), Locale.US)).isEqualTo("1 second");
        assertThat(this.formats.duration(Duration.ofDays(1).plusHours(12), Locale.US))
                .isEqualTo("1 day and 12 hours");
        assertThat(this.formats.duration(Duration.ofDays(366).plusMinutes(2).plusSeconds(3),
                Locale.US)).isEqualTo("1 year, 1 day, 2 minutes and 3 seconds");
        assertThat(this.formats.duration(Duration.ofMillis(400), Locale.US))
                .isEqualTo("0 seconds");
        assertThat(this.formats.length(punishment(PlayerVictim.of(STEVE), "", null),
                Locale.US)).isEqualTo("permanent");
        assertThat(this.formats.expiry(punishment(PlayerVictim.of(STEVE), "", null),
                Locale.US)).isEqualTo("never");
    }

    @Test
    void localesFallBackToTheirLanguageThenToEnglish() throws Exception {
        Files.writeString(this.dir.resolve("lang/de_DE.yml"), "operator:\n  console: Konsole\n");
        this.messages.load();

        assertThat(this.messages.renderPlain(MessageKey.OPERATOR_CONSOLE, Locale.GERMANY))
                .isEqualTo("Konsole");
        assertThat(this.messages.renderPlain(MessageKey.OPERATOR_CONSOLE,
                Locale.forLanguageTag("de-AT"))).isEqualTo("Konsole");
        assertThat(this.messages.renderPlain(MessageKey.TIME_NEVER, Locale.GERMANY))
                .isEqualTo("never");
        assertThat(this.messages.renderPlain(MessageKey.OPERATOR_CONSOLE, Locale.FRANCE))
                .isEqualTo("Console");
    }

    @Test
    void theEnglishFileIsCopiedOnceAndNeverRewritten() throws Exception {
        final Path english = this.dir.resolve("lang/en_US.yml");
        assertThat(english).exists();
        final String edited = "# mine\noperator:\n  console: The Server\n";
        Files.writeString(english, edited);

        this.messages.load();

        assertThat(Files.readString(english)).isEqualTo(edited);
        assertThat(this.messages.renderPlain(MessageKey.OPERATOR_CONSOLE, Locale.US))
                .isEqualTo("The Server");
        assertThat(this.messages.renderPlain(MessageKey.TIME_NEVER, Locale.US)).isEqualTo("never");
        assertThat(this.log.entries("warn")).extracting(RecordingLogger.Entry::format)
                .anyMatch(text -> text.contains("missing keys") && text.contains("time.never"));
    }

    @Test
    void brokenOrUnknownEntriesAreReportedNotFatal() throws Exception {
        Files.writeString(this.dir.resolve("lang/es_ES.yml"), "operator: [unclosed\n");
        Files.writeString(this.dir.resolve("lang/it_IT.yml"), "operator:\n  consol: Console\n");

        this.messages.load();

        assertThat(this.log.entries("warn")).extracting(RecordingLogger.Entry::format)
                .anyMatch(text -> text.contains("es_ES.yml") && text.contains("ignored"))
                .anyMatch(text -> text.contains("it_IT.yml") && text.contains("operator.consol"));
    }

    @Test
    void sendUsesTheAudiencesLocale() throws Exception {
        Files.writeString(this.dir.resolve("lang/de_DE.yml"), "operator:\n  console: Konsole\n");
        this.messages.load();
        final List<Component> received = new ArrayList<>();
        final Audience german = new Audience() {
            @Override
            public void sendMessage(final Component message) {
                received.add(message);
            }

            @Override
            public Pointers pointers() {
                return Pointers.builder().withStatic(Identity.LOCALE, Locale.GERMANY).build();
            }
        };

        this.messages.send(german, MessageKey.OPERATOR_CONSOLE);
        this.messages.send(Audience.empty(), MessageKey.OPERATOR_CONSOLE);

        assertThat(received).extracting(MessageServiceTest::plain).containsExactly("Konsole");
        assertThat(this.messages.localeOf(Audience.empty())).isEqualTo(Locale.US);
    }
}
