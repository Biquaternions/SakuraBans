package net.serlith.sakurabans.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.audit.AuditQuery;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.imports.ImportOptions;
import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.api.kind.AddressMode;
import net.serlith.sakurabans.api.kind.KindDefinition;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import org.junit.jupiter.api.Test;

class AdminBuildersTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void kindsValidateTheirModifiers() {
        final List<FieldError> errors = KindDefinition.builder("Not Valid", PunishmentType.KICK)
                .displayName(" ")
                .shadow(true)
                .permanentOnly(true)
                .requiredEvidence(Set.of("chat-snapshot"))
                .validate();

        assertThat(errors).extracting(FieldError::path, FieldError::code).containsExactlyInAnyOrder(
                tuple("id", Code.INVALID_IDENTIFIER),
                tuple("displayName", Code.BLANK),
                tuple("shadow", Code.NOT_ALLOWED),
                tuple("permanentOnly", Code.NOT_ALLOWED),
                tuple("requiredEvidence", Code.INVALID_FORMAT));
    }

    @Test
    void theBlacklistKindBuilds() {
        final KindDefinition blacklist = KindDefinition.builder("Blacklist", PunishmentType.BAN)
                .strictness(AddressStrictness.STRICT)
                .permanentOnly(true)
                .addressMode(AddressMode.AS_GIVEN)
                .build();

        assertThat(blacklist.id()).isEqualTo("blacklist");
        assertThat(blacklist.displayName()).isEqualTo("Blacklist");
        assertThat(blacklist.strictness()).contains(AddressStrictness.STRICT);
    }

    @Test
    void templateStepsNeedPositiveDurations() {
        assertThat(TemplateStep.of(null).duration()).isEmpty();
        assertThat(TemplateStep.of(Duration.ofHours(1)).duration()).contains(Duration.ofHours(1));
        assertThatThrownBy(() -> TemplateStep.of(Duration.ZERO))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void auditQueriesCheckTheirBounds() {
        assertThat(AuditQuery.builder().subject(null, "7").between(T0, T0).pageSize(0).validate())
                .extracting(FieldError::path)
                .containsExactlyInAnyOrder("subjectId", "from", "pageSize");
        assertThat(AuditQuery.builder().subject(SubjectType.PUNISHMENT, "7").build().pageSize())
                .isEqualTo(50);
    }

    @Test
    void importedPunishmentsKeepTheirHistory() {
        final ImportedPunishment.Builder ban = ImportedPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(UUID.randomUUID()), ConsoleOperator.INSTANCE, T0);

        final ImportedPunishment imported = ban.expiresAt(T0.plusSeconds(60))
                .ended(EndKind.REVOKED, T0.plusSeconds(30), ConsoleOperator.INSTANCE, "appeal")
                .externalId("hydra-42")
                .build();

        assertThat(imported.end()).hasValueSatisfying(end ->
                assertThat(end.kind()).isEqualTo(EndKind.REVOKED));
        assertThat(ban.expiresAt(T0).validate()).extracting(FieldError::path)
                .containsExactly("expiresAt");
        assertThat(ImportOptions.builder().batchSize(0).source("").validate())
                .extracting(FieldError::path).containsExactlyInAnyOrder("batchSize", "source");
    }

    @Test
    void actionsAndDecisionsAreValueObjects() {
        assertThat(Action.Punish.of("Shadow-Mute", null).kind()).isEqualTo("shadow-mute");
        assertThat(Action.ChangePunishment.of(Action.ChangePunishment.Operation.VOID, 3))
                .isEqualTo(Action.ChangePunishment.of(Action.ChangePunishment.Operation.VOID, 3));

        assertThat(Decision.allow().allowed()).isTrue();
        assertThat(Decision.allow().denial()).isEmpty();
        final Decision denied = Decision.deny(Decision.Denial.GUARD, "verify with 2FA first");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.denial()).contains(Decision.Denial.GUARD);
    }
}
