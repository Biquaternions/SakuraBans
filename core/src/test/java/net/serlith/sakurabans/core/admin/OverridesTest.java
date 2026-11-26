package net.serlith.sakurabans.core.admin;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.enforce.SakuraMessageFormatter;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.SettingType;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Network settings, message overrides and the formatter (plan S37a). */
class OverridesTest extends ServiceTest {

    @Override
    protected String configYaml() {
        return
                """
                reasons:
                  default: From the file
                """;
    }

    private static <T> T ok(final AdminResult<T> result) {
        Assertions.assertThat(result).isInstanceOf(AdminResult.Ok.class);
        return ((AdminResult.Ok<T>) result).value();
    }

    private static List<FieldError> invalid(final AdminResult<?> result) {
        Assertions.assertThat(result).isInstanceOf(AdminResult.ValidationFailed.class);
        return ((AdminResult.ValidationFailed<?>) result).errors();
    }

    @Test
    void theSchemaSaysWhatIsSharedAndEditable() {
        final List<SettingField> schema = this.settingsService.schema();
        Assertions.assertThat(schema).filteredOn(field -> field.key()
                .equals("enforcement.address-strictness")).singleElement().satisfies(field -> {
                    Assertions.assertThat(field.type()).isEqualTo(SettingType.ENUM);
                    Assertions.assertThat(field.options()).contains("STERN");
                    Assertions.assertThat(field.editable()).isTrue();
                    Assertions.assertThat(field.networkWide()).isTrue();
                    Assertions.assertThat(field.defaultValue()).isEqualTo("NORMAL");
                });
        Assertions.assertThat(schema).filteredOn(field -> field.key().equals("server.name"))
                .singleElement().satisfies(field -> {
                    Assertions.assertThat(field.editable()).isFalse();
                    Assertions.assertThat(field.networkWide()).isFalse();
                });
        Assertions.assertThat(schema).filteredOn(SettingField::restartRequired)
                .extracting(SettingField::key)
                .containsExactly("commands.labels");
    }

    @Test
    void overridesWinOverTheFileAndAreVersionedSyncedAndAudited() {
        final SettingsSnapshot before = this.settingsService.settings();
        Assertions.assertThat(before.version()).isEqualTo(1);
        Assertions.assertThat(before.values()).containsEntry("reasons.default", "From the file");
        Assertions.assertThat(before.origins()).containsEntry("reasons.default", ValueOrigin.FILE)
                .containsEntry("display.id-style", ValueOrigin.DEFAULT);

        final SettingsSnapshot after = ok(this.settingsService.update(Map.of(
                "reasons.default", "From the network",
                "enforcement.address-strictness", AddressStrictness.STERN,
                "warnings.expire-after", "7d",
                "scopes.categories", Map.of("pvp", List.of("arena-1", "arena-2"))),
                1, MODERATOR).join());
        Assertions.assertThat(after.version()).isEqualTo(2);
        Assertions.assertThat(after.values()).containsEntry("reasons.default",
                "From the network").containsEntry("enforcement.address-strictness", "STERN");
        Assertions.assertThat(after.origins()).containsEntry("reasons.default",
                ValueOrigin.OVERRIDE);
        Assertions.assertThat(this.config.settings().reasons().defaultReason())
                .isEqualTo("From the network");
        Assertions.assertThat(this.config.settings().enforcement().addressStrictness())
                .isEqualTo(AddressStrictness.STERN);
        Assertions.assertThat(this.config.settings().warnings().expireAfter())
                .isEqualTo(Duration.ofDays(7));
        Assertions.assertThat(this.config.settings().scopes().categories())
                .containsEntry("pvp", Set.of("arena-1", "arena-2"));
        Assertions.assertThat(this.published).contains(new SyncPacket.EntityChangedPacket(
                SubjectType.SETTINGS));

        // A reload of the files keeps them, and punishments use them.
        this.config.reload();
        final Punishment ban = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).build(),
                PunishOptions.defaults()).join()).punishment();
        Assertions.assertThat(ban.reason()).isEqualTo("From the network");

        Assertions.assertThat(this.settingsService.update(Map.of("reasons.default", "x"), 1,
                MODERATOR).join()).isInstanceOfSatisfying(AdminResult.Conflict.class,
                        conflict -> Assertions.assertThat(conflict.currentVersion())
                                .isEqualTo(2));
        final SettingsSnapshot reset = ok(this.settingsService.reset(Set.of("reasons.default"),
                2, MODERATOR).join());
        Assertions.assertThat(reset.origins()).containsEntry("reasons.default",
                ValueOrigin.FILE);
        Assertions.assertThat(this.config.settings().reasons().defaultReason())
                .isEqualTo("From the file");
    }

    @Test
    void wrongKeysAndValuesAreRefusedWithTheirPaths() {
        Assertions.assertThat(invalid(this.settingsService.update(Map.of(
                "server.name", "lobby", "nonsense", true), 1, MODERATOR).join()))
                .extracting(FieldError::path)
                .containsExactlyInAnyOrder("server.name", "nonsense");
        // Types first, then the rules between values (as for the files).
        Assertions.assertThat(invalid(this.settingsService.update(Map.of(
                "enforcement.address-strictness", "HARD"), 1, MODERATOR).join()))
                .extracting(FieldError::path)
                .containsExactly("enforcement.address-strictness");
        Assertions.assertThat(invalid(this.settingsService.update(Map.of(
                "alts.join-notification.threshold", 0), 1, MODERATOR).join()))
                .extracting(FieldError::path)
                .containsExactly("alts.join-notification.threshold");
        Assertions.assertThat(this.config.overrides()).isEmpty();
        Assertions.assertThat(this.settingsService.settings().version()).isEqualTo(1);
    }

    @Test
    void otherInstancesChangesArriveThroughTheSync() {
        final OverrideRepository repository = new OverrideRepository(this.storage);
        repository.changeSettings(GlobalScope.INSTANCE, Map.of("display.id-style",
                this.config.encode("BOTH")), 1, MODERATOR, this.clock.instant()).join();

        this.syncReceiver().handle(new SyncPacket.EntityChangedPacket(SubjectType.SETTINGS))
                .join();

        Assertions.assertThat(this.settingsService.settings().version()).isEqualTo(2);
        Assertions.assertThat(this.config.settings().display().idStyle().name())
                .isEqualTo("BOTH");
    }

    @Test
    void messagesAreOverriddenPerLocale() {
        Assertions.assertThat(this.messageTemplates.schema()).filteredOn(field -> field.key()
                .equals("punish.ban")).singleElement().satisfies(field ->
                Assertions.assertThat(field.placeholders()).contains("victim", "duration",
                        "scope", "reason", "display_id").doesNotContain("green", "prefix"));

        // Each locale counts its own versions, from 0 while it has no overrides.
        Assertions.assertThat(this.messageTemplates.messages(Locale.FRANCE).version()).isZero();
        final MessageSnapshot texts = ok(this.messageTemplates.update(Locale.US,
                Map.of("victim.warned", "<prefix><gold>Warned: <reason>"), 0, MODERATOR)
                .join());
        Assertions.assertThat(texts.texts()).containsEntry("victim.warned",
                "<prefix><gold>Warned: <reason>");
        Assertions.assertThat(texts.origins()).containsEntry("victim.warned",
                ValueOrigin.OVERRIDE).containsEntry("victim.muted", ValueOrigin.FILE);
        Assertions.assertThat(plain(this.messages.render(MessageKey.VICTIM_WARNED, Locale.US)))
                .contains("Warned:");
        Assertions.assertThat(this.published).contains(new SyncPacket.EntityChangedPacket(
                SubjectType.MESSAGE));

        // Tags are not checked: a typo is stored and renders as text.
        Assertions.assertThat(invalid(this.messageTemplates.update(Locale.US, Map.of(
                "victim.muted", "<red>Muted: <reasn>", "no.such.key", "x"), 1, MODERATOR)
                .join())).extracting(FieldError::path).containsExactly("no.such.key");
        ok(this.messageTemplates.update(Locale.US, Map.of("victim.muted", "<red>Muted: <reasn>"),
                1, MODERATOR).join());
        ok(this.messageTemplates.reset(Locale.US, Set.of("victim.warned"), 2, MODERATOR)
                .join());
        Assertions.assertThat(this.messageTemplates.messages(Locale.US).version()).isEqualTo(3);
        Assertions.assertThat(this.messageTemplates.messages(Locale.FRANCE).version()).isZero();
        Assertions.assertThat(plain(this.messages.render(MessageKey.VICTIM_WARNED, Locale.US)))
                .contains("You were warned");
        Assertions.assertThat(plain(this.messageTemplates.preview("<prefix><b>Hi <name>",
                Map.of("name", "<red>Steve")))).endsWith("Hi <red>Steve");
    }

    @Test
    void theFormatterRendersOurScreens() {
        final Punishment ban = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR)
                .duration(Duration.ofDays(1)).build(), PunishOptions.defaults()).join())
                .punishment();
        final SakuraMessageFormatter formatter = new SakuraMessageFormatter(
                this.enforcementMessages, new Formats(this.messages, this.config));

        Assertions.assertThat(plain(formatter.disconnectScreen(ban, Locale.US)))
                .contains("You are banned", "From the file");
        Assertions.assertThat(plain(formatter.summary(ban, Locale.US)))
                .contains("#" + ban.displayId());
        Assertions.assertThat(formatter.formatDuration(Duration.ofHours(25), Locale.US))
                .isEqualTo("1 day and 1 hour");
    }
}
