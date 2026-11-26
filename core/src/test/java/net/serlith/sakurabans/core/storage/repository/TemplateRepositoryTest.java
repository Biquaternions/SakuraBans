package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Templates in the database and the ladder history (S31); the integration test runs it too. */
class TemplateRepositoryTest extends DatabaseTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    private TemplateRepository templates;
    private PunishmentRepository punishments;
    private InetAddress shared;

    @BeforeEach
    void fixture() throws Exception {
        this.templates = new TemplateRepository(this.storage, AltPolicy.NONE);
        this.punishments = new PunishmentRepository(this.storage,
                new PunishmentMapper(Long::toString));
        this.shared = InetAddress.getByName("203.0.113.2");
        final PlayerRepository players = new PlayerRepository(this.storage);
        players.recordConnection(STEVE, "Steve", this.now.get(), this.shared, null).join();
        players.recordConnection(ALEX, "Alex", this.now.get(), this.shared, null).join();
    }

    private static TemplateDefinition hacking() {
        return TemplateDefinition.builder("hacking", PunishmentType.BAN)
                .ip(true)
                .scope(ServerScope.of("survival"))
                .reason("Hacking")
                .ladder(List.of(TemplateStep.of(Duration.ofDays(1)),
                        TemplateStep.of(null, List.of("say {victim} is gone"))))
                .ladderExpiry(Duration.ofDays(30))
                .actions(List.of("say once", "say twice"))
                .groups(Map.of("cheating", 2, "all", 1))
                .build();
    }

    private void punish(final Victim victim, final String template) {
        this.punishments.insert(new NewPunishment(PunishmentType.WARN, false,
                victim, GlobalScope.INSTANCE, "reason", CONSOLE, this.now.get(), null, null,
                Visibility.NORMAL, template, 0, List.of(), null), OverridePolicy.REJECT).join();
    }

    @Test
    void templatesRoundTripWithStepsGroupsAndActions() {
        final TemplateRepository.Write created = this.templates.create(hacking(), CONSOLE,
                this.now.get()).join();

        Assertions.assertThat(created).isInstanceOf(TemplateRepository.Write.Done.class);
        Assertions.assertThat(this.templates.all().join()).singleElement().satisfies(stored -> {
            Assertions.assertThat(stored.summary())
                    .isEqualTo(((TemplateRepository.Write.Done) created).value().summary());
            Assertions.assertThat(stored.ladder().get(1).actions())
                    .containsExactly("say {victim} is gone");
            Assertions.assertThat(stored.groups()).containsEntry("cheating", 2);
            Assertions.assertThat(stored.version()).isEqualTo(1);
            Assertions.assertThat(stored.ip()).isTrue();
            Assertions.assertThat(stored.scope()).contains(ServerScope.of("survival"));
        });
        Assertions.assertThat(this.templates.create(hacking(), CONSOLE,
                this.now.get()).join()).isInstanceOf(TemplateRepository.Write.Taken.class);
    }

    @Test
    void updatesCheckVersionsAndSkipNoChanges() {
        this.templates.create(hacking(), CONSOLE, this.now.get()).join();
        final TemplateDefinition changed = TemplateDefinition.builder("hacking", PunishmentType.BAN)
                .reason("Cheating").build();

        Assertions.assertThat(this.templates.update(hacking(), 1L, CONSOLE,
                this.now.get()).join()).isInstanceOf(TemplateRepository.Write.Unchanged.class);
        Assertions.assertThat(this.templates.update(changed, 3L, CONSOLE,
                this.now.get()).join()).isEqualTo(new TemplateRepository.Write.Conflict(1));
        final TemplateRepository.Write done = this.templates.update(changed, 1L, CONSOLE,
                this.now.get()).join();

        Assertions.assertThat(done).isInstanceOfSatisfying(TemplateRepository.Write.Done.class,
                write -> {
                    Assertions.assertThat(write.value().version()).isEqualTo(2);
                    Assertions.assertThat(write.value().groups()).isEmpty();
                });
        Assertions.assertThat(new AuditRepository(this.storage).about(SubjectType.TEMPLATE,
                        "hacking", 10).join())
                .extracting(entry -> entry.action())
                .containsExactly(AuditAction.UPDATE, AuditAction.CREATE);
    }

    @Test
    void deletingKeepsTheReferenceOnPunishments() {
        this.templates.create(hacking(), CONSOLE, this.now.get()).join();
        this.punish(PlayerVictim.of(STEVE), "hacking");

        Assertions.assertThat(this.templates.delete("hacking", 1, CONSOLE, this.now.get()).join())
                .isInstanceOf(TemplateRepository.Write.Done.class);

        Assertions.assertThat(this.templates.all().join()).isEmpty();
        Assertions.assertThat(this.punishments.byId(1).join().orElseThrow().template())
                .hasValueSatisfying(ref -> Assertions.assertThat(ref.name()).isEqualTo("hacking"));
    }

    @Test
    void historyCountsTheVictimOrItsAltsWhenShared() {
        this.punish(PlayerVictim.of(STEVE), "hacking");
        this.punish(PlayerVictim.of(STEVE), "xray");
        this.punish(PlayerVictim.of(ALEX), "hacking");
        this.punish(AddressVictim.of(this.shared), "hacking");

        Assertions.assertThat(this.templates.history(Set.of("hacking", "xray"),
                        PlayerVictim.of(STEVE), false, null, this.now.get()).join())
                .containsExactlyInAnyOrderEntriesOf(Map.of("hacking", 1L, "xray", 1L));
        Assertions.assertThat(this.templates.history(Set.of("hacking"), PlayerVictim.of(STEVE),
                true, null, this.now.get()).join()).containsEntry("hacking", 3L);
        Assertions.assertThat(this.templates.history(Set.of("hacking"),
                        AddressVictim.of(this.shared), false, null, this.now.get()).join())
                .containsEntry("hacking", 1L);
        Assertions.assertThat(this.templates.history(Set.of("hacking"), PlayerVictim.of(STEVE),
                        false, this.now.get().plusSeconds(1), this.now.get()).join())
                .isEmpty();
    }
}
