package net.serlith.sakurabans.core.command.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S70: /sb audit and /sb audit export. */
class AuditCommandsTest extends CommandTest {

    private static final UUID AUDITOR = UUID.fromString("5a0e1a7c-0d52-4b6e-8f0a-3f1d7a3a9b11");
    private static final String HASH =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
    private static final String WEBHOOK = "https://discord.example/api/webhooks/1/s3cr3t-t0ken";

    @Override
    protected String configYaml() {
        return "display:\n  censor-ip-addresses: true\n";
    }

    private AuditRepository audit;

    @BeforeEach
    void players() {
        this.audit = new AuditRepository(this.storage);
        this.known(STEVE, "Steve");
        this.known(ALEX, "Alex");
        this.known(MOD, "Mod");
        this.names.put(STEVE, "Steve");
        this.names.put(ALEX, "Alex");
        this.names.put(MOD, "Mod");
    }

    private void append(final long secondsAgo, final Actor actor, final AuditAction action,
            final SubjectType type, final String subject, final String old, final String updated) {
        this.audit.append(this.now.get().minusSeconds(secondsAgo), actor, action, type, subject,
                old, updated).join();
    }

    private FakeCommandActor auditor(final String... more) {
        final FakeCommandActor actor = FakeCommandActor.player(AUDITOR, "Auditor",
                Permissions.ADMIN_AUDIT);
        actor.grant(more);
        return actor;
    }

    private String output(final FakeCommandActor actor, final String line) {
        actor.clear();
        this.run(actor, line);
        return String.join("\n", actor.texts());
    }

    private static List<String> clicks(final Component component) {
        final List<String> commands = new ArrayList<>();
        final ClickEvent<?> click = component.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text text) {
            commands.add(text.value());
        }
        component.children().forEach(child -> commands.addAll(clicks(child)));
        return commands;
    }

    private void seed() {
        this.append(600, MODERATOR, AuditAction.CREATE, SubjectType.PUNISHMENT, "7", null,
                "BAN " + STEVE + " global permanent xray");
        this.append(500, MODERATOR, AuditAction.EDIT_REASON, SubjectType.PUNISHMENT, "7",
                "xray", "x-ray");
        this.append(400, CONSOLE, AuditAction.UPDATE, SubjectType.TEMPLATE, "hacking", "a", "b");
        this.append(300, CONSOLE, AuditAction.UPDATE, SubjectType.TEMPLATE,
                "server:skywars hacking", "a", "b");
        this.append(200, CONSOLE, AuditAction.UPDATE, SubjectType.SETTINGS, "settings global",
                "{reasons.default=(file), limits:console-cooldown=(file)}",
                "{reasons.default=Rules, limits:console-cooldown=5s}");
        this.append(100, MODERATOR, AuditAction.ERASE, SubjectType.PLAYER, STEVE.toString(),
                null, "names=2");
    }

    @Test
    void listsNewestFirstWithWhoWhenWhatAndClickThrough() {
        this.seed();

        final FakeCommandActor console = this.console;
        this.run(console, "sb audit");

        final List<String> texts = console.texts();
        Assertions.assertThat(texts.getFirst()).contains("Audit log (6, page 1/1)");
        Assertions.assertThat(texts.get(1)).contains("erase", "player Steve", "by Mod (in-game)",
                "names=2");
        Assertions.assertThat(texts.get(2)).contains("update", "settings global", "by Console",
                "{reasons.default=(file), limits:console-cooldown=(file)}"
                        + " → {reasons.default=Rules");
        Assertions.assertThat(texts.getLast()).contains("create", "#7", "by Mod", "BAN");
        Assertions.assertThat(clicks(console.components().getLast()))
                .containsExactly("/sakurabans info 7");
        Assertions.assertThat(clicks(console.components().get(1))).isEmpty();
    }

    @Test
    void filtersNarrowAndCombine() {
        this.seed();

        Assertions.assertThat(this.output(this.console, "sb audit --actor console"))
                .contains("(3,").doesNotContain("by Mod");
        Assertions.assertThat(this.output(this.console, "sb audit --actor Mod"))
                .contains("(3,").doesNotContain("by Console");
        Assertions.assertThat(this.output(this.console, "sb audit --actor " + MOD))
                .contains("(3,");
        Assertions.assertThat(this.output(this.console, "sb audit --player Steve"))
                .contains("(1,", "erase");
        Assertions.assertThat(this.output(this.console, "sb audit --id 7"))
                .contains("(2,", "create", "edit_reason");
        Assertions.assertThat(this.output(this.console, "sb audit --id #7")).contains("(2,");
        Assertions.assertThat(this.output(this.console, "sb audit --template hacking"))
                .contains("(2,", "skywars hacking");
        Assertions.assertThat(this.output(this.console, "sb audit --setting"
                + " limits:console-cooldown")).contains("(1,", "settings global");
        Assertions.assertThat(this.output(this.console, "sb audit --setting reasons.defaul"))
                .as("a key, not a prefix").contains("Nothing to show");
        Assertions.assertThat(this.output(this.console, "sb audit --action update"))
                .contains("(3,");
        Assertions.assertThat(this.output(this.console, "sb audit --action UPDATE"))
                .contains("(3,");
        Assertions.assertThat(this.output(this.console, "sb audit --since 250s"))
                .contains("(2,");
        Assertions.assertThat(this.output(this.console, "sb audit --since 250s --actor Mod"))
                .contains("(1,", "erase");
        Assertions.assertThat(this.output(this.console, "sb audit --actor console --action"
                + " update --since 450s")).contains("(3,");
        Assertions.assertThat(this.output(this.console, "sb audit --actor Mod --action update"))
                .contains("Nothing to show");
        Assertions.assertThat(this.output(this.console, "sb audit --player Steve --id 7"))
                .as("subjects differ: nothing is both").contains("Nothing to show");
    }

    @Test
    void badFiltersSayWhatIsWrongAndShowNothing() {
        this.seed();

        Assertions.assertThat(this.output(this.console, "sb audit --action smash"))
                .contains("There is no audit action smash", "create", "update");
        Assertions.assertThat(this.output(this.console, "sb audit --since soon"))
                .contains("soon");
        Assertions.assertThat(this.output(this.console, "sb audit --id zzz"))
                .contains("not a punishment id");
        Assertions.assertThat(this.output(this.console, "sb audit --player Nobody"))
                .contains("Nobody is known as Nobody");
        Assertions.assertThat(this.output(this.console, "sb audit --actor Nobody"))
                .contains("Nobody is known as Nobody");
    }

    @Test
    void pagesKeepTheirFilters() {
        for (int i = 0; i < 25; i++) {
            this.append(1000 - i, CONSOLE, AuditAction.UPDATE, SubjectType.TEMPLATE, "t" + i,
                    null, "n" + i);
        }
        this.append(5000, MODERATOR, AuditAction.DELETE, SubjectType.TEMPLATE, "old", null, null);

        this.run(this.console, "sb audit 2 --action update");

        final List<String> texts = this.console.texts();
        Assertions.assertThat(texts.getFirst()).contains("(25, page 2/3)");
        Assertions.assertThat(texts).hasSize(12);
        Assertions.assertThat(texts.get(1)).contains("template t14");
        Assertions.assertThat(texts.getLast()).contains("Page 2 of 3");
        Assertions.assertThat(clicks(this.console.components().getLast())).containsExactly(
                "/sakurabans audit 1 --action update", "/sakurabans audit 3 --action update");

        Assertions.assertThat(this.output(this.console, "sb audit 9")).contains("only 3 pages");
        Assertions.assertThat(this.output(this.console, "sb audit 3")).contains("page 3/3");
    }

    @Test
    void needsThePermission() {
        this.seed();
        final FakeCommandActor nobody = FakeCommandActor.player(UUID.randomUUID(), "Nobody");

        Assertions.assertThat(this.output(nobody, "sb audit")).doesNotContain("Audit log");
        Assertions.assertThat(this.output(nobody, "sb audit export")).doesNotContain("Exported");
        Assertions.assertThat(this.output(this.auditor(), "sb audit")).contains("Audit log");
    }

    // Masking

    private void addresses() {
        this.append(60, MODERATOR, AuditAction.CREATE, SubjectType.PUNISHMENT, "9", null,
                "BAN 198.51.100.77 global permanent evading from 198.51.100.78");
        this.append(50, MODERATOR, AuditAction.CREATE, SubjectType.PUNISHMENT, "10", null,
                "BAN 2001:db8:0:0:0:0:0:1 global permanent v6");
        this.append(40, MODERATOR, AuditAction.CREATE, SubjectType.PUNISHMENT, "11", null,
                "BAN 198.51.100.0/24 global permanent range");
        this.append(30, MODERATOR, AuditAction.ALLOW, SubjectType.ALLOW_LIST, "198.51.100.9",
                null, "family");
        this.append(20, MODERATOR, AuditAction.FORGET_ADDRESS, SubjectType.PLAYER,
                STEVE.toString(), "198.51.100.5", null);
        this.append(10, MODERATOR, AuditAction.APPLY, SubjectType.ADDRESS_HASH, HASH, null,
                "template spam (no punishment)");
        this.append(5, MODERATOR, AuditAction.RESET, SubjectType.VICTIM, "42", "uses", null);
    }

    @Test
    void withoutViewipsNoAddressSurvives() {
        this.addresses();

        final String text = this.output(this.auditor(), "sb audit");

        Assertions.assertThat(text).doesNotContain("198.51.100", "2001:db8:0:0", HASH, "9f86d081"
                + "884c7d65");
        Assertions.assertThat(text).contains("198.51.*.*", "2001:db8:*", "family");
        Assertions.assertThat(text).contains("198.51.*.*/24");
        // The hash shows as a short label, not as anything like an address.
        Assertions.assertThat(text).contains("address 9f86d081");
        Assertions.assertThat(text).contains("victim 42");
        Assertions.assertThat(text).doesNotContainPattern("\\d+\\.\\d+\\.\\d+\\.\\d+");
        Assertions.assertThat(this.output(this.auditor(), "sb audit --id 9"))
                .contains("198.51.*.*", "(1,");
    }

    @Test
    void withViewipsAndOnTheConsoleAddressesShowAsStored() {
        this.addresses();

        Assertions.assertThat(this.output(this.auditor(Permissions.VIEW_IPS), "sb audit"))
                .contains("198.51.100.77", "198.51.100.9", "198.51.100.5", "2001:db8:0:0:0:0:0:1")
                .doesNotContain(HASH);
        Assertions.assertThat(this.output(this.console, "sb audit")).contains("198.51.100.77");
    }

    @Test
    void masksAlsoApplyToThePunishmentChangesOfInfo() {
        this.addresses();
        this.append(1, MODERATOR, AuditAction.EDIT_REASON, SubjectType.PUNISHMENT, "1", "from"
                + " 198.51.100.77", "to");
        this.run(this.console, "ban Steve -r x");
        this.console.clear();

        final String masked = this.output(this.auditor(Permissions.INFO), "sb info 1");

        Assertions.assertThat(masked).contains("Changes:").doesNotContain("198.51.100.77");
    }

    @Test
    void secretsStayAsTheyWereWritten() {
        this.settingsService.change(GlobalScope.INSTANCE, Map.of("webhooks:url", WEBHOOK,
                "reasons.default", "Rules"), Set.of(), 1, CONSOLE).join();

        final String text = this.output(this.auditor(Permissions.VIEW_IPS), "sb audit");

        Assertions.assertThat(text).contains("webhooks:url=(secret)", "reasons.default=Rules")
                .doesNotContain(WEBHOOK, "s3cr3t");
        Assertions.assertThat(this.output(this.console, "sb audit --setting webhooks:url"))
                .contains("(1,").doesNotContain("s3cr3t");
    }

    // Export

    private JsonObject exported() throws IOException {
        final Path exports = this.dir.resolve("exports");
        try (var files = Files.list(exports)) {
            final List<Path> found = files.filter(file -> file.getFileName().toString()
                    .startsWith("audit-")).toList();
            Assertions.assertThat(found).hasSize(1);
            return JsonParser.parseString(Files.readString(found.getFirst())).getAsJsonObject();
        }
    }

    @Test
    void exportWritesTheFilteredEntriesAndSaysWhere() throws IOException {
        this.seed();

        this.run(this.console, "sb audit export --actor Mod");

        Assertions.assertThat(this.console.last()).contains("Exported 3 audit entries to"
                + " exports/audit-20261119-120000.json");
        final JsonObject json = this.exported();
        Assertions.assertThat(json.get("count").getAsLong()).isEqualTo(3);
        final JsonArray entries = json.getAsJsonArray("entries");
        Assertions.assertThat(entries).hasSize(3);
        final JsonObject first = entries.get(0).getAsJsonObject();
        Assertions.assertThat(first.get("action").getAsString()).isEqualTo("erase");
        Assertions.assertThat(first.get("subjectType").getAsString()).isEqualTo("player");
        Assertions.assertThat(first.get("subject").getAsString()).isEqualTo(STEVE.toString());
        Assertions.assertThat(first.get("new").getAsString()).isEqualTo("names=2");
        Assertions.assertThat(first.get("old").isJsonNull()).isTrue();
        Assertions.assertThat(first.getAsJsonObject("actor").get("name").getAsString())
                .isEqualTo("Mod");
        Assertions.assertThat(first.getAsJsonObject("actor").get("source").getAsString())
                .isEqualTo("in-game");
        Assertions.assertThat(first.get("at").getAsString())
                .isEqualTo(this.now.get().minusSeconds(100).toString());
        Assertions.assertThat(entries.get(2).getAsJsonObject().get("subject").getAsString())
                .isEqualTo("7");
    }

    @Test
    void exportWithoutMatchesWritesAnEmptyList() throws IOException {
        this.run(this.console, "sb audit export --action import");

        Assertions.assertThat(this.console.last()).contains("Exported 0 audit entries");
        Assertions.assertThat(this.exported().getAsJsonArray("entries")).isEmpty();
    }

    @Test
    void exportReadsInBatchesAndKeepsEveryEntryOnceInOrder() throws IOException {
        final int total = AuditCommands.EXPORT_BATCH * 2 + 37;
        final Instant base = this.now.get();
        for (int i = 0; i < total; i++) {
            // Many share a second, so the order's tie-break (the id) is used too.
            this.audit.append(base.minusSeconds(i / 7), CONSOLE, AuditAction.UPDATE,
                    SubjectType.TEMPLATE, "t" + i, null, "v" + i).join();
        }

        this.run(this.console, "sb audit export");

        final JsonObject json = this.exported();
        Assertions.assertThat(json.get("count").getAsLong()).isEqualTo(total);
        final List<String> subjects = new ArrayList<>();
        json.getAsJsonArray("entries").forEach(entry -> subjects.add(
                entry.getAsJsonObject().get("subject").getAsString()));
        Assertions.assertThat(subjects).doesNotHaveDuplicates().hasSize(total);
        // Newest first: the newest second holds t0..t6, the highest id first.
        Assertions.assertThat(subjects.subList(0, 7)).containsExactly("t6", "t5", "t4", "t3",
                "t2", "t1", "t0");
        Assertions.assertThat(subjects.getLast()).isEqualTo("t" + (total - 1));
    }

    @Test
    void exportMasksAddressesExceptOnTheConsole() throws IOException {
        this.addresses();
        this.audit = new AuditRepository(this.storage);

        this.run(this.auditor(), "sb audit export");
        final String masked = Files.readString(this.onlyExport());
        Assertions.assertThat(masked).doesNotContain("198.51.100", "2001:db8:0:0", HASH)
                .contains("198.51.*.*", "9f86d081");

        Files.delete(this.onlyExport());
        this.now.set(this.now.get().plusSeconds(1));
        this.run(this.console, "sb audit export");
        Assertions.assertThat(Files.readString(this.onlyExport())).contains("198.51.100.77",
                "198.51.100.5").doesNotContain(HASH);
    }

    private Path onlyExport() throws IOException {
        try (var files = Files.list(this.dir.resolve("exports"))) {
            return files.findFirst().orElseThrow();
        }
    }

    @Test
    void exportKeepsSecretsMasked() throws IOException {
        this.settingsService.change(GlobalScope.INSTANCE, Map.of("webhooks:url", WEBHOOK),
                Set.of(), 1, CONSOLE).join();

        this.run(this.console, "sb audit export");

        Assertions.assertThat(Files.readString(this.onlyExport())).contains("(secret)")
                .doesNotContain("s3cr3t");
    }

    @Test
    void sinceUsesTheClock() {
        this.seed();
        this.now.set(this.now.get().plus(Duration.ofHours(1)));

        Assertions.assertThat(this.output(this.console, "sb audit --since 30m"))
                .contains("Nothing to show");
        Assertions.assertThat(this.output(this.console, "sb audit --since 2h")).contains("(6,");
    }
}
